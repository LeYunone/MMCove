package com.mmcove.agent.mcp.external;

import com.mmcove.agent.mcp.support.McpToolSupport;
import com.mmcove.agent.vibe.PipelineRegistry;
import com.mmcove.agent.vibe.VibePromptComposerService;
import com.mmcove.agent.vibe.VibeRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外 Vibe 流水线 MCP 工具集(经标准 MCP 协议暴露给 Claude Code/Cursor 等外部 AI 客户端)。
 *
 * <p><b>刻意不注册为 Spring Bean</b>:在 {@code McpServerConfig} 里手动 new
 * (防内部 ToolRegistry 扫进内部 Agent 工具池,同 {@link McpKnowledgeTools} 约定)。
 *
 * <p>六工具构成「组装 → 执行上报 → 压缩恢复 → 终态治理」闭环:
 * <ul>
 *   <li>composeVibePrompt:任务 → 结构化流水线提示词(含 runId + 知识锚点 + 阶段地图 + 门禁);</li>
 *   <li>reportStage:每阶段开始/完成上报进度与产物(服务端跟踪;产物超限截断保存不拖垮进度);</li>
 *   <li>getRunContext:对话被压缩丢失指令时恢复(剩余阶段+产物索引)——
 *       本工具的 description 常驻外部 AI 的 system 层不参与压缩,是恢复的触发器;</li>
 *   <li>getRunArtifact:取回阶段产物全文(恢复场景的权威数据源);</li>
 *   <li>abortRun:任务放弃/无法继续时的终态出口(防僵尸 RUNNING);</li>
 *   <li>getPipelineStage:流水线/阶段目录查询。</li>
 * </ul>
 *
 * <p>入参全 String(规避 MethodToolCallback 数值转换 NPE)。
 * compose/getRunContext 返回纯文本提示词(数千 token,JSON 转义膨胀且损害逐字遵循),
 * reportStage/getPipelineStage 返回统一 JSON。
 *
 * @since 2026-09-11
 */
@Slf4j
@RequiredArgsConstructor
public class McpVibeTools {

    private final VibePromptComposerService composerService;
    private final VibeRunService vibeRunService;
    private final PipelineRegistry pipelineRegistry;
    private final KbAccessService kbAccessService;

    /** 组装流水线提示词(核心工具) */
    @Tool(description = "生成结构化编码任务执行提示词(Vibe Coding 流水线)。当用户要求修复 bug/缺陷/报错、"
            + "开发新功能、重构代码、补测试等编码任务时,在开始编码前调用本工具,把返回的提示词作为本次任务的"
            + "执行指令,按其中的阶段顺序逐阶段执行(需求分析→实现→互审→测试→门禁)。"
            + "task 传用户的原始任务描述(尽量保留原话,包含报错信息/文件名等细节);"
            + "用户话术提到其他产品线时传 productLineName,否则留空用默认产品线。"
            + "返回包含:runId(任务执行实例,后续上报/恢复用)、意图结论、产品线背景、知识库锚点、"
            + "阶段地图(每阶段角色指令/产出物/门禁)、执行约定。")
    public String composeVibePrompt(
            @ToolParam(description = "用户原始任务描述(保留原话与报错细节)") String task,
            @ToolParam(description = "产品线名称(可选;用户话术提到的产品线,留空用默认产品线)", required = false) String productLineName) {
        return guard(() -> {
            if (McpToolSupport.isBlank(task)) {
                return McpToolSupport.err("task 不能为空");
            }
            VibePromptComposerService.ComposedPrompt result = composerService
                    .compose(task, productLineName, null, false);
            log.info("[Vibe] composeVibePrompt: run={}, pipeline={}, stages={}, chunks={}, degraded={}",
                    result.runCode(), result.pipelineCode(), result.stageCount(),
                    result.knowledgeChunks(), result.degraded());
            return result.prompt();
        });
    }

    /** 阶段推进上报 */
    @Tool(description = "上报编码任务的阶段执行进度并挂存阶段产物。执行 composeVibePrompt 返回的流水线时,"
            + "每个阶段开始/完成时调用本工具:runId 传提示词头部的 runId(如 RUN-XXXXXX);stageCode 传阶段编码"
            + "(如 reproduce-locate);status 传 starting/completed/failed;"
            + "阶段有产出物(分析报告/方案/评审清单/测试报告等文本)时传 artifactTitle+artifactContent 一并上传,"
            + "summary 传一句话摘要。服务端据此跟踪进度,并在上下文丢失时供恢复。"
            + "产物超大时服务端会截断保存并在返回中警告,进度不受影响。")
    public String reportStage(
            @ToolParam(description = "执行实例短码(composeVibePrompt 返回,如 RUN-XXXXXX)") String runId,
            @ToolParam(description = "阶段编码(提示词阶段地图中每阶段括号内,如 root-cause)") String stageCode,
            @ToolParam(description = "starting/completed/failed") String status,
            @ToolParam(description = "一句话进度说明(可选)", required = false) String summary,
            @ToolParam(description = "产物标题(可选;有产物时与 artifactContent 成对传)", required = false) String artifactTitle,
            @ToolParam(description = "产物内容(可选;markdown 文本)", required = false) String artifactContent) {
        return guard(() -> {
            VibeRunService.StageReport report = vibeRunService.reportStage(
                    runId, stageCode, status, summary, artifactTitle, artifactContent);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("runId", report.runCode());
            data.put("stageCode", report.stageCode());
            data.put("status", report.status());
            data.put("completedStages", report.completedStages());
            data.put("totalStages", report.totalStages());
            data.put("runCompleted", report.runCompleted());
            if (report.artifactWarning() != null) {
                data.put("artifactWarning", report.artifactWarning());
            }
            String desc = "阶段[" + report.stageCode() + "] " + report.status()
                    + ",总进度 " + report.completedStages() + "/" + report.totalStages()
                    + (report.runCompleted() ? ";全部阶段完成,流水线已收敛" : ";继续下一阶段")
                    + (report.artifactWarning() != null ? ";" + report.artifactWarning() : "");
            return McpToolSupport.ok(desc, data);
        });
    }

    /** 放弃/中止任务 */
    @Tool(description = "中止一个编码任务执行实例(状态机终态出口)。当任务确认无法继续、用户明确放弃,"
            + "或发现任务前提不成立不再推进时调用,避免实例停留在进行中污染后续恢复。"
            + "runId 传执行实例短码;reason 传中止原因(会存档)。已完成的实例无需中止。")
    public String abortRun(
            @ToolParam(description = "执行实例短码(如 RUN-XXXXXX)") String runId,
            @ToolParam(description = "中止原因(一句话)", required = false) String reason) {
        return guard(() -> McpToolSupport.ok(vibeRunService.abortRun(runId, reason), null));
    }

    /** 取回阶段产物全文 */
    @Tool(description = "取回编码任务某阶段的产物全文(服务端存档)。上下文被压缩恢复后、或本地工作目录产物不可信时,"
            + "用本工具取回已完成阶段的分析结论/互审清单/测试报告等——尤其互审结论,避免重新引入已被拦截的问题。"
            + "stageCode 留空返回该任务全部产物索引;需要 getRunContext 里列出的某阶段全文时带上它的阶段编码。")
    public String getRunArtifact(
            @ToolParam(description = "执行实例短码(如 RUN-XXXXXX)") String runId,
            @ToolParam(description = "阶段编码(可选;留空返回全部产物索引)", required = false) String stageCode) {
        return guard(() -> {
            String result = vibeRunService.getRunArtifact(runId, stageCode);
            log.info("[Vibe] getRunArtifact: runId={}, stageCode={}", runId, stageCode);
            return result;
        });
    }

    /** 上下文恢复(防压缩) */
    @Tool(description = "恢复编码任务的执行上下文。当你在执行编码任务,但发现缺少任务指令/阶段地图/门禁要求,"
            + "或用户要求'继续之前的任务/接着做',或对话被压缩后找不到原始提示词时,调用本工具恢复。"
            + "runId 留空自动使用当前产品线最近活跃的任务实例。"
            + "返回原始执行指令完整快照 + 当前进度(已完成阶段/产物索引)——从断点继续执行,不要重复已完成阶段。")
    public String getRunContext(
            @ToolParam(description = "执行实例短码(可选;提示词头部的 runId,留空自动找最近活跃实例)",
                    required = false) String runId) {
        return guard(() -> {
            String context = vibeRunService.getRunContext(runId);
            log.info("[Vibe] getRunContext: runId={}", runId);
            return context;
        });
    }

    /** 流水线/阶段目录 */
    @Tool(description = "查看编码流水线的阶段定义。当用户想了解有哪些流水线、或想查看特定流水线"
            + "(如 bug-fix/feature-dev)每个阶段的门禁与产出物要求时调用。"
            + "pipelineCode 留空返回全部可用流水线及其阶段清单。")
    public String getPipelineStage(
            @ToolParam(description = "流水线编码(可选;如 bug-fix,留空返回全部)", required = false) String pipelineCode) {
        return guard(() -> {
            List<Map<String, Object>> pipelines;
            if (McpToolSupport.notBlank(pipelineCode)) {
                pipelines = List.of(pipelineRegistry.byCode(pipelineCode.trim())
                        .map(this::pipelineSummary)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "流水线不存在: " + pipelineCode + "(留空可查全部)")));
            } else {
                // 产品线视角:本线私有副本 + 全局共享(私有优先覆盖同名意图)
                pipelines = pipelineRegistry.pipelinesForLine(kbAccessService.currentProductLineId()).stream()
                        .map(this::pipelineSummary)
                        .toList();
            }
            return McpToolSupport.ok("共 " + pipelines.size() + " 条流水线", pipelines);
        });
    }

    // ==================== 内部 ====================

    private Map<String, Object> pipelineSummary(PipelineRegistry.EffectivePipeline pipeline) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pipelineCode", pipeline.template().getPipelineCode());
        summary.put("name", pipeline.template().getName());
        summary.put("intentType", pipeline.template().getIntentType());
        summary.put("description", pipeline.template().getDescription());
        List<Map<String, Object>> stages = pipeline.stages().stream()
                .map(stage -> {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("stageCode", stage.stageCode());
                    s.put("name", stage.name());
                    s.put("subAgentCount", stage.subAgentCount());
                    s.put("deliverable", stage.deliverable());
                    s.put("gateRule", stage.gateRule());
                    return s;
                })
                .toList();
        summary.put("stages", stages);
        return summary;
    }

    private String guard(java.util.function.Supplier<String> action) {
        try {
            return action.get();
        } catch (NeedConfirmException e) {
            return McpToolSupport.err(e.getMessage());
        } catch (IllegalArgumentException e) {
            return McpToolSupport.err(e.getMessage());
        } catch (Exception e) {
            log.error("MCP Vibe 工具执行失败: {}", e.getMessage(), e);
            return McpToolSupport.err("执行失败: " + e.getMessage());
        }
    }
}
