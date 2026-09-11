package com.mmcove.agent.vibe;

import com.mmcove.agent.llm.gateway.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Vibe 任务意图分类器:关键词前置 + LLM 兜底(仿 {@code AgentRouter} 二段式骨架)。
 *
 * <p>分类对象是流水线(候选来自 {@link PipelineRegistry} 活跃流水线,
 * keywords/name 均为运营数据,不硬编码)。关键词命中即返回不调 LLM(多数任务零 LLM 成本);
 * 并列或未命中时 LLM 定主;LLM 异常/无效回兜底意图,外部 AI 侧零失败。
 *
 * @since 2026-09-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VibeIntentClassifier {

    /** 意图来源:关键词命中/LLM判定/单流水线直选/兜底 */
    public static final String SOURCE_KEYWORD = "KEYWORD";
    public static final String SOURCE_LLM = "LLM";
    public static final String SOURCE_SINGLE = "SINGLE";
    public static final String SOURCE_FALLBACK = "FALLBACK";

    private final PipelineRegistry pipelineRegistry;
    private final LlmGateway llmGateway;
    private final VibeProperties vibeProperties;

    /**
     * 分类结果:type 为 intent_type;source 标注判定路径;note 为兜底时的提示。
     */
    public record VibeIntent(String type, String source, String note) {
    }

    /** 对编码任务做意图分类 */
    public VibeIntent classify(String task) {
        List<PipelineRegistry.EffectivePipeline> pipelines = pipelineRegistry.allPipelines();
        if (pipelines.isEmpty()) {
            throw new IllegalStateException("未配置任何流水线,请先执行种子 SQL 或在管理端配置");
        }
        if (pipelines.size() == 1) {
            return new VibeIntent(pipelines.get(0).template().getIntentType(), SOURCE_SINGLE, null);
        }

        // 1. 关键词前置(命中即返回,不调 LLM)
        PipelineRegistry.EffectivePipeline keywordMatch = matchByKeyword(task, pipelines);
        if (keywordMatch != null) {
            log.info("[Vibe] 意图关键词命中: {} - {}", keywordMatch.template().getIntentType(),
                    keywordMatch.template().getName());
            return new VibeIntent(keywordMatch.template().getIntentType(), SOURCE_KEYWORD,
                    "若该流水线与任务性质不符,请让用户明确任务类型后重新调用");
        }

        // 2. LLM 兜底(候选动态生成,返回值须在活跃意图集合内才采信)
        if (vibeProperties.isLlmIntentEnabled()) {
            try {
                String candidates = pipelines.stream()
                        .map(p -> String.format("- %s: %s", p.template().getIntentType(),
                                p.template().getDescription()))
                        .collect(Collectors.joining("\n"));
                String prompt = String.format(
                        "以下是可用的编码任务流水线列表：\n%s\n\n用户任务：%s\n\n请判断该任务属于哪条流水线，只返回对应的类型代码（如 BUG_FIX），不要返回任何其他内容。",
                        candidates, task);
                String detected = llmGateway.detectIntent(prompt);
                if (detected != null) {
                    String type = detected.trim();
                    boolean valid = pipelines.stream()
                            .anyMatch(p -> p.template().getIntentType().equalsIgnoreCase(type));
                    if (valid) {
                        log.info("[Vibe] 意图 LLM 判定: {}", type);
                        return new VibeIntent(type.toUpperCase(), SOURCE_LLM, null);
                    }
                    log.warn("[Vibe] LLM 返回无效意图类型: {}, 走兜底", type);
                }
            } catch (Exception e) {
                log.warn("[Vibe] LLM 意图分类异常(走兜底): {}", e.getMessage());
            }
        }

        // 3. 兜底意图(低置信,提示词 meta 会注明)
        return new VibeIntent(vibeProperties.getDefaultIntentType(), SOURCE_FALLBACK,
                "意图置信度低,如判断有误请让用户补充任务描述后重新调用");
    }

    /** 关键词打分:流水线名命中 +3,keywords 命中 +1;>0 且不并列才返回 */
    private PipelineRegistry.EffectivePipeline matchByKeyword(
            String task, List<PipelineRegistry.EffectivePipeline> pipelines) {
        String msg = task.toLowerCase();
        PipelineRegistry.EffectivePipeline best = null;
        int bestScore = 0;
        boolean tied = false;

        for (PipelineRegistry.EffectivePipeline pipeline : pipelines) {
            int score = computeKeywordScore(msg, pipeline);
            if (score > bestScore) {
                bestScore = score;
                best = pipeline;
                tied = false;
            } else if (score == bestScore && score > 0) {
                tied = true;
            }
        }
        return bestScore > 0 && !tied ? best : null;
    }

    private int computeKeywordScore(String msg, PipelineRegistry.EffectivePipeline pipeline) {
        var template = pipeline.template();
        int score = 0;
        if (template.getName() != null && msg.contains(template.getName().toLowerCase())) {
            score += 3;
        }
        if (template.getKeywords() == null || template.getKeywords().isBlank()) {
            return score;
        }
        String[] keywords = template.getKeywords().split("[，,、\\s]+");
        for (String keyword : keywords) {
            if (keyword.length() >= 2 && msg.contains(keyword.toLowerCase())) {
                score += 1;
            }
        }
        return score;
    }
}
