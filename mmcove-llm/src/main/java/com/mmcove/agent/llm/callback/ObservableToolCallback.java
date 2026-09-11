package com.mmcove.agent.llm.callback;

import com.mmcove.agent.common.context.KnowledgeContextHolder;
import com.mmcove.agent.common.context.PlatformUserContext;
import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.enums.SseEventType;
import com.mmcove.agent.infra.sse.SseEventStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可观测工具回调装饰器。
 * <p>
 * 包装每个 ToolCallback，在工具调用前后发出 task_progress 事件：
 * - 调用前：发出 thought + action 事件
 * - 调用后：发出 observation 事件
 * <p>
 * 通过 ThinkingTurnConsumer 回调接口解耦持久化逻辑，
 * 避免 mmcove-llm → mmcove-core 循环依赖。
 */
@Slf4j
public class ObservableToolCallback implements ToolCallback {

    /** 工具参数最大展示长度，防止前端渲染超长内容 */
    private static final int MAX_TOOL_ARGS_DISPLAY = 200;
    /** observation 最大展示长度 */
    private static final int MAX_OBSERVATION_DISPLAY = 500;
    /** 慢调用告警阈值(毫秒,失败兜底):超此耗时告警;真超时由工具内部 Dubbo/LMM timeout 保证 */
    private static final long SLOW_CALL_THRESHOLD_MS = 10_000;

    private final ToolCallback delegate;
    private final SseEmitter emitter;
    private final String sessionId;
    private final String subTaskId;
    private final SseEventStore sseEventStore;
    private final ThinkingTurnConsumer thinkingTurnConsumer;
    private final AtomicInteger turnCounter;
    /**
     * 当前请求所属分组。用于在工具调用线程(reactor-netty)恢复 TokenAuthContext,
     * 使 @Tool 方法能通过 {@code TokenAuthContext.get().getGroupName()} 拿到真实分组。
     * 为 null 时不覆盖(保持线程原有的上下文)。
     */
    private final String groupName;
    /** 实体记忆回调(L3):工具调用后拦截完整结果存实体(deviceKey/groupId),nullable(不传则不记忆)。 */
    private final EntityMemoryConsumer entityMemoryConsumer;
    /** 关联 ID(失败兜底):贯穿一次请求的日志/SSE 事件,nullable(旧路径不传)。 */
    private final String correlationId;
    /**
     * 构造时(在请求/虚拟线程,PlatformUserContext 已由拦截器+snapshot/restore 填好)捕获的平台用户快照
     * (JWT uid/eid/sysTag)。doCall 在 reactor 工具调用线程恢复,使 @Tool(如 getDevicesByUserId)
     * 能读 PlatformUserContext.getUserId()。普通 ThreadLocal 跨 reactor 调度线程不继承,这里桥接。null 时不覆盖。
     */
    private final PlatformUserContext.Snapshot platformUserSnapshot;
    /**
     * 构造时捕获的知识库 ID 快照(由 KnowledgeBaseRouter 在意图识别期写入 KnowledgeContextHolder)。
     * doCall 在 reactor 工具线程恢复,使 searchKnowledge 工具能读到 kbId。null 时不覆盖。
     */
    private final KnowledgeContextHolder.Snapshot knowledgeSnapshot;

    /**
     * 思考轮次消费接口（函数式），用于解耦持久化逻辑。
     * 由调用方注入具体实现（如 AgentOrchestrator 中注入 ThinkingChainRecorder）。
     */
    @FunctionalInterface
    public interface ThinkingTurnConsumer {
        void accept(String sessionId, String subTaskId, int turnIndex,
                    String thought, String actionTool, String actionArgs, String observation);
    }

    public ObservableToolCallback(ToolCallback delegate,
                                  SseEmitter emitter,
                                  String sessionId,
                                  String subTaskId,
                                  SseEventStore sseEventStore,
                                  ThinkingTurnConsumer thinkingTurnConsumer,
                                  AtomicInteger turnCounter) {
        this(delegate, emitter, sessionId, subTaskId, sseEventStore,
                thinkingTurnConsumer, turnCounter, null, null);
    }

    public ObservableToolCallback(ToolCallback delegate,
                                  SseEmitter emitter,
                                  String sessionId,
                                  String subTaskId,
                                  SseEventStore sseEventStore,
                                  ThinkingTurnConsumer thinkingTurnConsumer,
                                  AtomicInteger turnCounter,
                                  String groupName,
                                  EntityMemoryConsumer entityMemoryConsumer) {
        this(delegate, emitter, sessionId, subTaskId, sseEventStore,
                thinkingTurnConsumer, turnCounter, groupName, entityMemoryConsumer, null);
    }

    public ObservableToolCallback(ToolCallback delegate,
                                  SseEmitter emitter,
                                  String sessionId,
                                  String subTaskId,
                                  SseEventStore sseEventStore,
                                  ThinkingTurnConsumer thinkingTurnConsumer,
                                  AtomicInteger turnCounter,
                                  String groupName,
                                  EntityMemoryConsumer entityMemoryConsumer,
                                  String correlationId) {
        this.delegate = delegate;
        this.emitter = emitter;
        this.sessionId = sessionId;
        this.subTaskId = subTaskId;
        this.sseEventStore = sseEventStore;
        this.thinkingTurnConsumer = thinkingTurnConsumer;
        this.turnCounter = turnCounter;
        this.groupName = groupName;
        this.entityMemoryConsumer = entityMemoryConsumer;
        this.correlationId = correlationId;
        // 在构造线程捕获 PlatformUserContext 快照(此时已由拦截器+controller snapshot/restore 填好 JWT 身份),
        // 供 doCall 在 reactor 工具线程恢复。未登录/旧路径(snapshot=null)则不覆盖。
        this.platformUserSnapshot = PlatformUserContext.snapshot();
        this.knowledgeSnapshot = KnowledgeContextHolder.snapshot();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return doCall(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return doCall(toolInput, toolContext);
    }

    private String doCall(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        int turnIndex = turnCounter.getAndIncrement();

        // 在工具调用线程(reactor-netty)恢复 TokenAuthContext.groupName + PlatformUserContext(JWT 身份):
        // 普通 ThreadLocal 跨 reactor 调度线程不继承,@Tool(如 LmmDeviceControlTools.getDevicesByUserId)
        // 读 PlatformUserContext.getUserId() 会拿到 null。此处由装饰器层(构造时在请求线程捕获快照)恢复。
        TokenAuthContext authCtx = TokenAuthContext.get();
        String previousGroupName = authCtx.getGroupName();
        boolean groupNameOverridden = groupName != null && !groupName.equals(previousGroupName);
        if (groupNameOverridden) {
            authCtx.setGroupName(groupName);
        }
        PlatformUserContext.Snapshot previousPlatform = PlatformUserContext.snapshot();
        if (platformUserSnapshot != null) {
            PlatformUserContext.restore(platformUserSnapshot);
        }
        KnowledgeContextHolder.Snapshot previousKb = KnowledgeContextHolder.snapshot();
        if (knowledgeSnapshot != null) {
            KnowledgeContextHolder.restore(knowledgeSnapshot);
        }

        try {
            // 1. 发出 thought 事件
            String thoughtContent = "需要调用工具 " + toolName + " 来完成任务";
            sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_PROGRESS, subTaskId,
                    buildProgressPayload(turnIndex, "thought", thoughtContent, null, null));

            // 2. 发出 action 事件（toolArgs 截断展示，防止敏感信息泄露）
            String displayArgs = toolInput != null && toolInput.length() > MAX_TOOL_ARGS_DISPLAY
                    ? toolInput.substring(0, MAX_TOOL_ARGS_DISPLAY) + "..."
                    : toolInput;
            sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_PROGRESS, subTaskId,
                    buildProgressPayload(turnIndex, "action",
                            "调用工具: " + toolName, toolName, displayArgs));

            // 3. 调用原始工具(失败兜底:记录耗时 + 慢调用告警;真超时由工具内部 Dubbo/LMM timeout 保证)
            String result;
            long callStart = System.currentTimeMillis();
            try {
                if (toolContext != null) {
                    result = delegate.call(toolInput, toolContext);
                } else {
                    result = delegate.call(toolInput);
                }
            } catch (Exception e) {
                log.error("工具调用失败: {} [cid={}]", toolName, correlationId, e);
                result = "工具调用失败: " + e.getMessage();
            }
            long callCost = System.currentTimeMillis() - callStart;
            if (callCost > SLOW_CALL_THRESHOLD_MS) {
                log.warn("慢调用告警: {} 耗时 {}ms [cid={}] (超阈值 {}ms)", toolName, callCost, correlationId, SLOW_CALL_THRESHOLD_MS);
            }

            // 4. 发出 observation 事件
            String observationContent = result != null && result.length() > MAX_OBSERVATION_DISPLAY
                    ? result.substring(0, MAX_OBSERVATION_DISPLAY) + "..."
                    : result;
            sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_PROGRESS, subTaskId,
                    buildProgressPayload(turnIndex, "observation", observationContent, null, null));

            // 5. 检测 needsConfirmation，通过 SSE 事件直接推送给前端（绕过 LLM）
            if (emitConfirmationIfNeeded(result)) {
                // 确认事件已通过 SSE 推送给前端，返回给 LLM 简洁的提示文本，
                // 避免让 LLM 看到并原样输出 JSON 给用户
                result = "操作正在等待用户确认。确认按钮已显示在前端界面中。"
                        + "请在回复中向用户简要说明操作内容，并提示用户点击确认按钮执行。"
                        + "不要声称操作已成功，操作将在用户确认后自动执行。";
            }

            // 5.5 检测生图结果(_imageEvent),通过 SSE image 事件推送给前端(绕过 LLM)。
            // 不修改 result,让 LLM 看到完整结果以组织文本回复。
            emitImageEventIfNeeded(result);
            // 5.6 检测 UI 渲染指令(_uiRenderEvent),通过 SSE ui_render 事件推送给前端。
            emitUiRenderEventIfNeeded(result);

            // 6. 通过回调持久化思考轮次
            if (thinkingTurnConsumer != null) {
                try {
                    thinkingTurnConsumer.accept(sessionId, subTaskId, turnIndex,
                            thoughtContent, toolName, toolInput, observationContent);
                } catch (Exception e) {
                    log.warn("思考轮次保存失败: {}", e.getMessage());
                }
            }

            // 7. L3 实体记忆:拦截工具完整结果(非截断),回调存实体(resolveDeviceOrGroup 的 deviceKey/groupId)
            if (entityMemoryConsumer != null) {
                try {
                    entityMemoryConsumer.accept(sessionId, toolName, toolInput, result);
                } catch (Exception e) {
                    log.warn("实体记忆保存失败: {}", e.getMessage());
                }
            }

            return result;
        } finally {
            // 恢复线程原有的 groupName + PlatformUserContext,避免污染(reactor 线程池复用)
            if (groupNameOverridden) {
                authCtx.setGroupName(previousGroupName);
            }
            PlatformUserContext.restore(previousPlatform);
            KnowledgeContextHolder.restore(previousKb);
        }
    }

    /**
     * 检测工具返回结果中是否包含 needsConfirmation，如果有则通过 SSE 事件直接推送确认信息给前端。
     * 这确保了前端始终能收到确认事件，不依赖 LLM 是否在回复中转发 JSON。
     *
     * @return true 如果检测到确认请求并成功推送 SSE 事件
     */
    private boolean emitConfirmationIfNeeded(String toolResult) {
        if (toolResult == null || !toolResult.contains("needsConfirmation")) {
            return false;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(toolResult);
            if (root.has("needsConfirmation") && root.get("needsConfirmation").asBoolean()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                if (root.has("confirmationToken")) {
                    payload.put("confirmationToken", root.get("confirmationToken").asText());
                }
                if (root.has("summary")) {
                    payload.put("summary", root.get("summary").asText());
                }
                if (root.has("expiresAt")) {
                    payload.put("expiresAt", root.get("expiresAt").asText());
                }
                sseEventStore.emitEvent(emitter, sessionId,
                        SseEventType.CONFIRMATION_REQUIRED, subTaskId, payload);
                return true;
            }
        } catch (Exception e) {
            log.debug("确认事件解析失败（非JSON结果）: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 检测工具返回结果中是否为生图结果(_imageEvent=true),如果是则通过 SSE image 事件
     * 把图片信息推送给前端(绕过 LLM,确保前端总能收到图片)。
     *
     * <p>与 {@link #emitConfirmationIfNeeded} 不同,本方法<b>不修改</b>返回给 LLM 的结果,
     * 让 LLM 看到完整的生图成功信息以组织自然语言回复。
     *
     * <p>payload 固定结构:{@code {url, prompt, size, model, success}},前端按此解析渲染图片。
     *
     * <p><b>双重解析说明</b>:Spring AI 的 {@code DefaultToolCallResultConverter} 会把工具方法
     * 返回的 JSON 字符串再 JSON 序列化一次,使其变成<b>字符串字面量</b>(外层带引号、内层引号被转义)。
     * 此时 {@code mapper.readTree(toolResult)} 得到的是 {@code TextNode} 而非 {@code ObjectNode},
     * 直接 {@code root.get("_imageEvent")} 会返回 null,导致 image 事件永远不被推送。
     * 因此这里在首次解析后判断:若为文本节点,需对其文本内容做二次解析,拿到真正的对象节点。
     */
    private void emitImageEventIfNeeded(String toolResult) {
        if (toolResult == null || !toolResult.contains("_imageEvent")) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(toolResult);
            // 解包 DefaultToolCallResultConverter 的字符串字面量包装
            if (root.isTextual()) {
                root = mapper.readTree(root.asText());
            }
            JsonNode flag = root.get("_imageEvent");
            if (flag == null || !flag.asBoolean()) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", root.has("imageUrl") ? root.get("imageUrl").asText() : null);
            payload.put("prompt", root.has("prompt") ? root.get("prompt").asText() : null);
            payload.put("size", root.has("size") ? root.get("size").asText() : null);
            payload.put("model", root.has("model") ? root.get("model").asText() : null);
            payload.put("success", true);
            sseEventStore.emitEvent(emitter, sessionId,
                    SseEventType.IMAGE, subTaskId, payload);
            log.info("[ObservableToolCallback] image 事件已推送: url={}",
                    root.path("imageUrl").asText());
        } catch (Exception e) {
            log.warn("image 事件解析失败(非JSON结果): {}", e.getMessage());
        }
    }

    /**
     * 检测工具返回结果中是否为 UI 渲染指令(_uiRenderEvent=true),
     * 是则通过 SSE ui_render 事件把 {component, props, target, mode, group, title, reason}
     * 推送给前端(绕过 LLM,确保前端总能收到渲染指令)。
     * 与 {@link #emitImageEventIfNeeded} 一样不修改返回给 LLM 的结果。
     */
    private void emitUiRenderEventIfNeeded(String toolResult) {
        if (toolResult == null || !toolResult.contains("_uiRenderEvent")) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(toolResult);
            // 解包 DefaultToolCallResultConverter 的字符串字面量包装(同 image 事件)
            if (root.isTextual()) {
                root = mapper.readTree(root.asText());
            }
            JsonNode flag = root.get("_uiRenderEvent");
            if (flag == null || !flag.asBoolean()) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("component", root.path("component").asText(null));
            JsonNode propsNode = root.get("props");
            if (propsNode != null) {
                payload.put("props", propsNode.isTextual() ? propsNode.asText() : mapper.writeValueAsString(propsNode));
            } else {
                payload.put("props", "{}");
            }
            payload.put("target", root.path("target").asText("panel"));
            payload.put("mode", root.path("mode").asText("stack"));
            payload.put("group", root.has("group") ? root.get("group").asText() : null);
            payload.put("title", root.has("title") ? root.get("title").asText() : null);
            payload.put("reason", root.has("reason") ? root.get("reason").asText() : null);
            sseEventStore.emitEvent(emitter, sessionId,
                    SseEventType.UI_RENDER, subTaskId, payload);
            log.info("[ObservableToolCallback] ui_render 事件已推送: component={}",
                    root.path("component").asText());
        } catch (Exception e) {
            log.warn("ui_render 事件解析失败(非JSON结果): {}", e.getMessage());
        }
    }

    private Map<String, Object> buildProgressPayload(int turnIndex, String step,
                                                      String content, String toolName, String toolArgs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("turnIndex", turnIndex);
        payload.put("step", step);
        payload.put("content", content);
        if (correlationId != null) {
            payload.put("cid", correlationId);
        }
        if (toolName != null) {
            payload.put("toolName", toolName);
        }
        if (toolArgs != null) {
            payload.put("toolArgs", toolArgs);
        }
        return payload;
    }
}
