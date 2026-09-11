package com.mmcove.agent.vibe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.model.entity.VibeRun;
import com.mmcove.agent.common.model.entity.VibeRunArtifact;
import com.mmcove.agent.infra.persistence.repository.VibeRunArtifactRepository;
import com.mmcove.agent.infra.persistence.repository.VibeRunRepository;
import com.mmcove.agent.mcp.external.KbAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Vibe 执行实例服务:阶段推进上报 + 上下文恢复(防压缩第 2 层保险的服务端落点)。
 *
 * <p>进度状态源:{@code vibe_run.completed_stages} JSON 数组(权威)——
 * 阶段完成与产物上报解耦,无产物的 completed 也不丢进度。
 *
 * <p>恢复策略:getRunContext 返回「原始提示词快照 + 当前进度段」——
 * 快照保证指令完整(阶段地图/门禁/知识锚点一次给全),进度段标明
 * 已完成阶段与产物索引,指示外部 AI 从断点继续、不重复已完成阶段。
 * runId 为空时自动取当前产品线最近活跃(RUNNING)实例——runId 在压缩中
 * 幸存与否都能恢复。
 *
 * @since 2026-09-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VibeRunService {

    private final VibeRunRepository vibeRunRepository;
    private final VibeRunArtifactRepository vibeRunArtifactRepository;
    private final PipelineRegistry pipelineRegistry;
    private final KbAccessService kbAccessService;
    private final VibeProperties vibeProperties;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** reportStage 的 status 合法值 */
    public static final String STAGE_STARTING = "starting";
    public static final String STAGE_COMPLETED = "completed";
    public static final String STAGE_FAILED = "failed";

    /**
     * 阶段推进上报结果。
     */
    public record StageReport(String runCode, String stageCode, String status,
            int completedStages, int totalStages, boolean runCompleted, String artifactWarning) {
    }

    /**
     * 上报阶段进度并挂存产物。
     *
     * <p><b>synchronized(并发防丢)</b>:并行子 agent(如 peer-review 的多视角互审)可能同时上报,
     * completed_stages 是"读-改-写"整存整取,无锁并发会互相覆盖丢阶段完成记录。
     * 方法级锁足够——reportStage 是每阶段 2 次的低频写,无性能争议。
     *
     * @param runId            执行实例短码(RUN-xxx)
     * @param stageCode        阶段编码
     * @param status           starting/completed/failed
     * @param summary          一句话进度说明(可空)
     * @param artifactTitle    产物标题(与 artifactContent 成对出现才落库)
     * @param artifactContent  产物内容(文本)
     */
    public synchronized StageReport reportStage(String runId, String stageCode, String status, String summary,
            String artifactTitle, String artifactContent) {
        VibeRun run = requireRun(runId);
        List<PipelineRegistry.EffectiveStage> stages = effectiveStagesOf(run);
        validateStageCode(stages, stageCode);
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        if (!STAGE_STARTING.equals(normalizedStatus) && !STAGE_COMPLETED.equals(normalizedStatus)
                && !STAGE_FAILED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("status 仅支持 starting/completed/failed,收到: " + status);
        }

        run.setCurrentStageCode(stageCode);

        // 1) 进度先落库(权威状态优先——产物处理失败绝不能拖垮进度,否则一次超限即造僵尸 RUNNING)
        Set<String> completed = parseCompleted(run);
        if (STAGE_COMPLETED.equals(normalizedStatus)) {
            completed.add(stageCode);
        }
        run.setCompletedStages(toJson(completed));
        boolean allDone = stages.stream().allMatch(s -> completed.contains(s.stageCode()));
        run.setStatus(allDone ? "COMPLETED" : "RUNNING");
        vibeRunRepository.update(run);

        // 2) 产物挂存(有标题+有内容才落库,与 status 无关);超限截断保存而非原子失败
        String artifactWarning = null;
        if (artifactTitle != null && !artifactTitle.isBlank()
                && artifactContent != null && !artifactContent.isBlank()) {
            String content = artifactContent;
            if (content.length() > vibeProperties.getArtifactMaxChars()) {
                content = content.substring(0, vibeProperties.getArtifactTruncateChars())
                        + "\n\n...(原文 " + artifactContent.length() + " 字符超上限 "
                        + vibeProperties.getArtifactMaxChars() + ",已截断保存;全文请留本地工作目录)";
                artifactWarning = "产物超限已截断保存(" + artifactContent.length() + "→"
                        + vibeProperties.getArtifactTruncateChars() + " 字符)";
                log.warn("[Vibe] 产物超限截断: run={}, stage={}, 原长 {}",
                        run.getRunCode(), stageCode, artifactContent.length());
            }
            VibeRunArtifact artifact = new VibeRunArtifact();
            artifact.setRunId(run.getId());
            artifact.setStageCode(stageCode);
            artifact.setTitle(artifactTitle.trim());
            artifact.setContent(content);
            artifact.setSummary(summary);
            vibeRunArtifactRepository.insert(artifact);
            log.info("[Vibe] 产物上报: run={}, stage={}, title={}", run.getRunCode(), stageCode, artifactTitle);
        }

        log.info("[Vibe] 阶段上报: run={}, stage={}, status={}, 进度 {}/{}",
                run.getRunCode(), stageCode, normalizedStatus, completed.size(), stages.size());
        return new StageReport(run.getRunCode(), stageCode, normalizedStatus,
                completed.size(), stages.size(), allDone, artifactWarning);
    }

    /**
     * 放弃/中止任务(状态机终态出口):AI 判定任务无法继续或用户明确放弃时调用。
     */
    public String abortRun(String runId, String reason) {
        VibeRun run = requireRun(runId);
        if ("COMPLETED".equals(run.getStatus())) {
            throw new IllegalArgumentException("任务已完成,无需中止: " + run.getRunCode());
        }
        run.setStatus("ABORTED");
        vibeRunRepository.update(run);
        log.info("[Vibe] 任务中止: run={}, reason={}", run.getRunCode(), reason);
        return "执行实例 " + run.getRunCode() + " 已标记 ABORTED"
                + (reason != null && !reason.isBlank() ? "(原因: " + reason + ")" : "");
    }

    /**
     * 取回阶段产物全文(恢复场景的关键一环:压缩后本地产物不可信,服务端存档是权威)。
     *
     * @param runId     执行实例短码
     * @param stageCode 阶段编码(可空=返回该 run 全部产物索引)
     */
    public String getRunArtifact(String runId, String stageCode) {
        VibeRun run = requireRun(runId);
        List<VibeRunArtifact> artifacts = vibeRunArtifactRepository.findByRunId(run.getId());
        if (stageCode != null && !stageCode.isBlank()) {
            List<VibeRunArtifact> matched = artifacts.stream()
                    .filter(a -> a.getStageCode().equals(stageCode.trim()))
                    .toList();
            if (matched.isEmpty()) {
                throw new IllegalArgumentException("该阶段无产物上报: " + stageCode + "(可用阶段见 getRunContext)");
            }
            StringBuilder sb = new StringBuilder(4096);
            sb.append("# 产物全文 [").append(run.getRunCode()).append(" / ").append(stageCode).append("]\n");
            for (VibeRunArtifact artifact : matched) {
                sb.append("\n## ").append(artifact.getTitle())
                        .append(artifact.getSummary() != null ? " — " + artifact.getSummary() : "")
                        .append("\n\n").append(artifact.getContent()).append('\n');
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# 产物索引 [").append(run.getRunCode()).append("](需要全文用 getRunArtifact 带 stageCode 取回)\n");
        for (VibeRunArtifact artifact : artifacts) {
            sb.append("- [").append(artifact.getStageCode()).append("] ").append(artifact.getTitle())
                    .append(artifact.getSummary() != null && !artifact.getSummary().isBlank()
                            ? " — " + artifact.getSummary() : "")
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * 恢复执行上下文(防压缩):原始提示词快照 + 当前进度段。
     *
     * @param runId 执行实例短码(可空=当前产品线最近活跃实例)
     */
    public String getRunContext(String runId) {
        VibeRun run = runId == null || runId.isBlank()
                ? latestRunningRun()
                : requireRun(runId.trim());
        if (run == null) {
            throw new IllegalArgumentException(
                    "当前产品线没有进行中的任务实例,请让用户描述新任务并调用 composeVibePrompt");
        }

        List<PipelineRegistry.EffectiveStage> stages = effectiveStagesOf(run);
        Set<String> completed = parseCompleted(run);
        List<VibeRunArtifact> artifacts = vibeRunArtifactRepository.findByRunId(run.getId());
        String pipelineName = pipelineRegistry.byId(run.getPipelineId())
                .map(p -> p.template().getName())
                .orElseGet(() -> run.getIntentType() + "(配置已停用,按快照继续)");

        StringBuilder sb = new StringBuilder(8192);
        sb.append("# Vibe 任务执行上下文恢复\n\n");
        sb.append("> runId: ").append(run.getRunCode())
                .append(" | 流水线: ").append(pipelineName)
                .append(" | 状态: ").append(run.getStatus()).append('\n');
        sb.append("\n## 一、原始任务与执行指令(完整快照,按此继续)\n\n")
                .append(run.getComposedPrompt() == null ? "(快照缺失)" : run.getComposedPrompt())
                .append('\n');

        sb.append("\n## 二、当前进度(从断点继续,不要重复已完成阶段)\n");
        for (int i = 0; i < stages.size(); i++) {
            PipelineRegistry.EffectiveStage stage = stages.get(i);
            boolean done = completed.contains(stage.stageCode());
            String marker = done ? "已完成"
                    : stage.stageCode().equals(run.getCurrentStageCode()) ? "进行中" : "待执行";
            sb.append("- 阶段").append(i + 1).append(" ").append(stage.name())
                    .append(" (").append(stage.stageCode()).append("): ").append(marker).append('\n');
        }
        sb.append("\n进度: ").append(completed.size()).append('/').append(stages.size());

        if (!artifacts.isEmpty()) {
            sb.append("\n\n## 三、已完成阶段的产物索引(已上报服务端存档;需要全文时用 getRunArtifact(runId, stageCode) 取回——"
                    + "恢复场景本地文件不可信,服务端存档是权威,尤其互审结论)\n");
            for (VibeRunArtifact artifact : artifacts) {
                sb.append("- [").append(artifact.getStageCode()).append("] ").append(artifact.getTitle())
                        .append(artifact.getSummary() != null && !artifact.getSummary().isBlank()
                                ? " — " + artifact.getSummary() : "")
                        .append('\n');
            }
        }
        sb.append("\n请从下一个未完成阶段继续执行,继续用 reportStage 上报进度。\n");
        return sb.toString();
    }

    /** 管理端/REST 预览用:取实例详情 */
    public VibeRun findRun(String runId) {
        return vibeRunRepository.findByRunCode(runId)
                .orElseThrow(() -> new IllegalArgumentException("执行实例不存在: " + runId));
    }

    public List<VibeRunArtifact> artifactsOf(Long runId) {
        return vibeRunArtifactRepository.findByRunId(runId);
    }

    // ==================== 内部 ====================

    private VibeRun requireRun(String runId) {
        return vibeRunRepository.findByRunCode(runId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "执行实例不存在: " + runId + "(请确认 composeVibePrompt 返回的 runId)"));
    }

    /** 当前产品线最近活跃实例(优先本调用方 token 归属,防跨调用方劫持) */
    private VibeRun latestRunningRun() {
        Long lineId = kbAccessService.currentProductLineId();
        if (lineId == null) {
            throw new IllegalArgumentException("未指定 runId 且当前 MCP 未配置默认产品线,无法定位任务实例");
        }
        Long ownerTokenId = com.mmcove.agent.common.context.TokenAuthContext.get().getTokenId();
        return vibeRunRepository.findLatestRunningForOwner(lineId, ownerTokenId).orElse(null);
    }

    /** 执行实例按 pipeline_id 反查流水线(不受意图改配影响) */
    private PipelineRegistry.EffectivePipeline pipelineOf(VibeRun run) {
        return pipelineRegistry.byId(run.getPipelineId())
                .orElseThrow(() -> new IllegalStateException(
                        "执行实例对应的流水线已不可用(runId=" + run.getRunCode() + "),请联系管理员"));
    }

    /**
     * run 的生效阶段序列(状态机校验与收敛基准),三级回退:
     * ① 完整快照(新格式,EffectiveStage JSON 数组)——脱离活配置,停用流水线/阶段也不杀在跑 run;
     * ② code 数组(旧格式)——按全局流水线过滤;
     * ③ 无固化——全局流水线全集(compose 前的存量 run)。
     */
    private List<PipelineRegistry.EffectiveStage> effectiveStagesOf(VibeRun run) {
        if (run.getEffectiveStages() != null && !run.getEffectiveStages().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(run.getEffectiveStages());
                if (root.isArray() && !root.isEmpty() && root.get(0).isObject()) {
                    // 新格式:完整快照直接反序列化,完全不依赖活配置(①级)
                    return MAPPER.convertValue(root,
                            MAPPER.getTypeFactory().constructCollectionType(List.class,
                                    PipelineRegistry.EffectiveStage.class));
                }
                // 旧格式:code 数组按全局流水线过滤(②级)
                List<PipelineRegistry.EffectiveStage> all = pipelineOf(run).stages();
                List<String> codes = new ArrayList<>();
                root.forEach(n -> codes.add(n.asText()));
                return all.stream()
                        .filter(s -> codes.contains(s.stageCode()))
                        .sorted(java.util.Comparator.comparingInt(s -> codes.indexOf(s.stageCode())))
                        .toList();
            } catch (Exception e) {
                log.warn("[Vibe] effective_stages 解析失败(回退全集): {}", e.getMessage());
            }
        }
        return pipelineOf(run).stages();
    }

    private Set<String> parseCompleted(VibeRun run) {
        Set<String> codes = new LinkedHashSet<>();
        if (run.getCompletedStages() == null || run.getCompletedStages().isBlank()) {
            return codes;
        }
        try {
            MAPPER.readTree(run.getCompletedStages())
                    .forEach(n -> codes.add(n.asText()));
        } catch (Exception e) {
            log.warn("[Vibe] completed_stages 解析失败(视为空): {}", e.getMessage());
        }
        return codes;
    }

    private String toJson(Set<String> codes) {
        try {
            return MAPPER.writeValueAsString(new ArrayList<>(codes));
        } catch (Exception e) {
            log.warn("[Vibe] completed_stages 序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    private void validateStageCode(List<PipelineRegistry.EffectiveStage> stages, String stageCode) {
        boolean exists = stages.stream().anyMatch(s -> s.stageCode().equals(stageCode));
        if (!exists) {
            throw new IllegalArgumentException("阶段编码不属于本流水线: " + stageCode
                    + "(可用: " + stages.stream().map(PipelineRegistry.EffectiveStage::stageCode)
                            .reduce((a, b) -> a + "," + b).orElse("") + ")");
        }
    }
}
