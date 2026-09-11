package com.mmcove.agent.rag.routing;

import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.infra.persistence.repository.AgentKnowledgeBaseRepository;
import com.mmcove.agent.infra.persistence.repository.KnowledgeBaseRepository;
import com.mmcove.agent.llm.gateway.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库场景路由器。
 *
 * <p>镜像 {@code com.mmcove.agent.core.agent.AgentRouter} 的两段式(关键词 → LLM),
 * 但路由对象是"知识库"。在第一次意图识别期由 {@code AgentOrchestrator} 调用:
 * <ol>
 *   <li>候选集 = 当前 Agent 关联的启用知识库({@code agent_knowledge_base});</li>
 *   <li>关键词匹配(kb.keywords/name/description) → 唯一高分直选(省 LLM);</li>
 *   <li>LLM 选库(候选 description 拼 prompt → {@link LlmGateway#detectIntent}) → top-1;</li>
 *   <li>兜底:priority 最高的库 / null。</li>
 * </ol>
 *
 * <p>选库结果由调用方写入 {@code KnowledgeContextHolder},供 searchKnowledge 工具读取。
 *
 * @since 2026-08-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseRouter {

    private final AgentKnowledgeBaseRepository agentKnowledgeBaseRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final LlmGateway llmGateway;

    /**
     * 为当前提问选择最相关的知识库。
     *
     * @param userMessage 用户消息
     * @param agentId     当前 Agent ID(决定候选集)
     * @return 选中的知识库;无候选/选不出返回 null(调用方跳过知识检索)
     */
    public KnowledgeBase route(String userMessage, String agentId) {
        // 1. 候选集:该 Agent 关联的启用知识库(按 agentId 隔离;项目多租户未启用,不依赖 TenantContext)
        List<Long> kbIds = agentKnowledgeBaseRepository.findEnabledKbIdsByAgent(agentId);
        if (kbIds.isEmpty()) {
            log.debug("Agent [{}] 未关联任何知识库,跳过知识检索", agentId);
            return null;
        }
        List<KnowledgeBase> candidates = knowledgeBaseRepository.findEnabledByIds(kbIds);
        if (candidates.isEmpty()) {
            return null;
        }

        // 2. 单库直选
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 3. 关键词匹配(命中且唯一高分直选,省 LLM)
        KnowledgeBase keywordMatch = matchByKeyword(userMessage, candidates);
        if (keywordMatch != null) {
            log.info("关键词匹配到知识库: {} - {}", keywordMatch.getId(), keywordMatch.getName());
            return keywordMatch;
        }

        // 4. LLM 选库(候选 prompt 拼装照搬 AgentRouter.detectAgent 行 56-63)
        String promptText = candidates.stream()
                .map(kb -> String.format("- %d: %s - %s", kb.getId(), kb.getName(),
                        kb.getDescription() == null ? "" : kb.getDescription()))
                .collect(Collectors.joining("\n"));
        String prompt = String.format(
                "以下是可用的知识库列表：\n%s\n\n用户消息：%s\n\n请从上面的列表中选择与用户问题最相关的知识库，只返回对应的 ID（数字），不要返回任何其他内容。",
                promptText, userMessage);
        String detectedId = llmGateway.detectIntent(prompt);
        if (detectedId != null) {
            detectedId = detectedId.trim();
            try {
                Long id = Long.parseLong(detectedId);
                for (KnowledgeBase kb : candidates) {
                    if (kb.getId().equals(id)) {
                        log.info("LLM 选库匹配: {} - {}", kb.getId(), kb.getName());
                        return kb;
                    }
                }
                log.warn("LLM 选库返回无效 ID: {}, 候选: {}", detectedId, kbIds);
            } catch (NumberFormatException e) {
                log.warn("LLM 选库返回非数字 ID: {}", detectedId);
            }
        }

        // 5. 兜底:priority 最高的库
        KnowledgeBase fallback = candidates.stream()
                .max(Comparator.comparingInt(k -> k.getPriority() == null ? 0 : k.getPriority()))
                .orElse(null);
        log.info("知识库选库兜底(priority 最高): {}", fallback == null ? "无" : fallback.getName());
        return fallback;
    }

    /**
     * 关键词匹配(镜像 AgentRouter.matchByKeyword):并列(tied)返回 null,交给 LLM 兜底。
     */
    private KnowledgeBase matchByKeyword(String userMessage, List<KnowledgeBase> candidates) {
        String msg = userMessage.toLowerCase();
        KnowledgeBase best = null;
        int bestScore = 0;
        boolean tied = false;
        for (KnowledgeBase kb : candidates) {
            int score = computeKeywordScore(msg, kb);
            if (score > bestScore) {
                bestScore = score;
                best = kb;
                tied = false;
            } else if (score == bestScore && score > 0) {
                tied = true;
            }
        }
        if (bestScore > 0 && !tied) {
            return best;
        }
        return null;
    }

    /**
     * 关键词评分(对标 AgentRouter.computeKeywordScore):
     * name 命中 +3,keywords 字段命中 +2(专门关键词权重高),description 关键词命中 +1。
     */
    private int computeKeywordScore(String msg, KnowledgeBase kb) {
        int score = 0;
        if (kb.getName() != null && msg.contains(kb.getName().toLowerCase())) {
            score += 3;
        }
        if (kb.getKeywords() != null && !kb.getKeywords().isBlank()) {
            String[] keywords = kb.getKeywords().split("[，,、\\s]+");
            for (String kw : keywords) {
                if (kw.length() >= 2 && msg.contains(kw.toLowerCase())) {
                    score += 2;
                }
            }
        }
        if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
            String[] words = kb.getDescription().split("[，,、\\s]+");
            for (String w : words) {
                if (w.length() >= 2 && msg.contains(w.toLowerCase())) {
                    score += 1;
                }
            }
        }
        return score;
    }
}
