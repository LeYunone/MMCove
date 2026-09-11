package com.mmcove.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.context.PlatformUserContext;
import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.model.dto.ChatResponse;
import com.mmcove.agent.common.model.dto.RoleChatRequest;
import com.mmcove.agent.common.model.entity.ApiTokenUsageLog;
import com.mmcove.agent.core.orchestrator.AgentOrchestrator;
import com.mmcove.agent.core.dialog.DialogManager;
import com.mmcove.agent.common.enums.MessageRole;
import com.mmcove.agent.common.model.entity.Conversation;
import com.mmcove.agent.llm.gateway.DynamicChannelHolder;
import com.mmcove.agent.service.ChannelLoadBalancer;
import com.mmcove.agent.service.DynamicChatModelFactory;
import com.mmcove.agent.service.QuotaService;
import com.mmcove.agent.service.RoleMessageService;
import com.mmcove.agent.service.UsageLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI Chat Completions 兼容接口。
 * POST /v1/chat/completions
 *
 * 同一个路径支持两种模式：
 * - 有 roleInfo → 同步 JSON 响应（大屏设备调用）
 * - 无 roleInfo → SSE 流式响应（原有调用方）
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ChatCompletionsController {

    private final AgentOrchestrator agentOrchestrator;
    private final RoleMessageService roleMessageService;
    private final ChannelLoadBalancer channelLoadBalancer;
    private final DynamicChatModelFactory dynamicChatModelFactory;
    private final QuotaService quotaService;
    private final UsageLogService usageLogService;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;
    private final DialogManager dialogManager;

    /**
     * SSE 流式模式入口（前端 fetch 需带 {@code Accept: text/event-stream}）。
     * <p>
     * 必须独立为返回 {@link SseEmitter} 的 mapping：包在 {@code ResponseEntity<?>} 里返回时，
     * Spring 6.2 会尝试用 HttpMessageConverter 序列化 SseEmitter 对象本身而失败
     * (No converter for SseEmitter with preset Content-Type 'text/event-stream')。
     * 声明为 SseEmitter 返回类型后，由 ResponseBodyEmitterReturnValueHandler 正确接管。
     */
    @PostMapping(value = "/chat/completions", headers = "Accept=text/event-stream")
    public SseEmitter chatCompletionsSse(@RequestBody String requestBody, HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        // 禁止代理/Nginx/CDN 缓冲 SSE 响应，确保每个 chunk 实时推送到客户端（不累积）。
        // X-Accel-Buffering: no 是事实标准，被 Nginx、Node http-proxy、各类网关识别。
        // no-transform 防止中间层对响应体做压缩/转换（会破坏 SSE 的分块边界）。
        httpResponse.setHeader("X-Accel-Buffering", "no");
        httpResponse.setHeader("Cache-Control", "no-cache, no-transform");

        TokenAuthContext context = TokenAuthContext.get();
        log.info("[ChatCompletions] SSE 流式模式: maskedKey={}, tokenId={}",
                context.getMaskedTokenKey(), context.getTokenId());
        try {
            JsonNode jsonNode = objectMapper.readTree(requestBody);
            return handleAgentChatSse(jsonNode, context, httpRequest);
        } catch (Exception e) {
            log.error("[ChatCompletions] SSE 请求解析失败: {}", e.getMessage(), e);
            SseEmitter emitter = new SseEmitter();
            emitError(emitter, "请求解析失败: " + e.getMessage());
            return emitter;
        }
    }

    /**
     * Chat Completions 接口（兼容 OpenAI 格式）。
     * <p>
     * 同一 URL 按 Accept header 路由：带 {@code Accept: text/event-stream} 的请求
     * 走 {@link #chatCompletionsSse}（SSE 流式），其余走本方法（同步 JSON，通常携带 roleInfo）。
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestBody String requestBody, HttpServletRequest httpRequest) {
        TokenAuthContext context = TokenAuthContext.get();
        Long tokenId = context.getTokenId();

        log.info("[ChatCompletions] 开始处理: maskedKey={}, tokenId={}",
                context.getMaskedTokenKey(), tokenId);

        try {
            JsonNode jsonNode = objectMapper.readTree(requestBody);
            JsonNode roleInfoNode = jsonNode.get("roleInfo");

            if (roleInfoNode != null && !roleInfoNode.isNull()) {
                log.info("[ChatCompletions] 检测到 roleInfo，走同步模式");
                return handleRoleChatSync(jsonNode, context, httpRequest);
            } else {
                // 无 roleInfo 的流式请求应由 chatCompletionsSse(Accept: text/event-stream) 处理。
                // 走到本分支说明客户端未带 Accept header：SseEmitter 无法在 ResponseEntity 中返回，
                // 此处直接提示客户端补全 header，避免再次触发 HttpMessageNotWritableException。
                log.warn("[ChatCompletions] 无 roleInfo 且缺少 Accept: text/event-stream，无法进入流式模式");
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("error", Map.of(
                                "message", "流式请求请添加 Accept: text/event-stream 请求头",
                                "type", "invalid_request_error"
                        )));
            }

        } catch (Exception e) {
            log.error("[ChatCompletions] 解析请求失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", Map.of(
                            "message", "请求解析失败: " + e.getMessage(),
                            "type", "server_error"
                    )));
        }
    }

    /**
     * 处理带 roleInfo 的角色聊天请求（同步 JSON 响应）。
     * 嵌入配额预扣/后扣/日志逻辑。
     */
    private ResponseEntity<?> handleRoleChatSync(JsonNode jsonNode, TokenAuthContext context, HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        LocalDateTime requestStartTime = LocalDateTime.now();
        int preConsumed = 0;
        int inputTextLength = 0;

        try {
            RoleChatRequest request = objectMapper.treeToValue(jsonNode, RoleChatRequest.class);
            String groupName = context.getGroupName();
            String model = request.getModel();
            inputTextLength = calculateInputTextLength(request);

            // 预扣费
            preConsumed = quotaService.preConsumeQuota(context, model);
            if (preConsumed == 0) {
                return ResponseEntity.status(429)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("error", Map.of(
                                "message", "配额不足",
                                "type", "quota_exceeded"
                        )));
            }

            // 根据分组加载 MCP 工具(接入六道防线:L2 路由收敛 + cid + 熔断;同步路径 emitter=null)
            List<ToolCallback> groupTools = agentOrchestrator.getRoutedToolCallbacks(
                    request.getUserAsk(), groupName, null, null);
            log.info("[ChatCompletions] RoleChat 路由收敛工具: groupName={}, toolCount={}", groupName, groupTools.size());

            // 收集流式结果（带工具）
            StringBuilder contentBuilder = new StringBuilder();
            roleMessageService.buildRoleChatContext(request, groupName, groupTools)
                    .doOnNext(chunk -> {
                        if (chunk != null && !chunk.isEmpty()) {
                            contentBuilder.append(chunk);
                        }
                    })
                    .blockLast();

            String content = contentBuilder.toString();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            LocalDateTime requestEndTime = LocalDateTime.now();

            // 后扣费（使用估算 Token 数，因为流式模式无法精确统计）
            int estimatedInputTokens = estimateTokens(content.length() / 2);
            int estimatedOutputTokens = estimateTokens(content.length());
            int actualCost = quotaService.postConsumeQuota(context, estimatedInputTokens, estimatedOutputTokens,
                    model, null, preConsumed, responseTimeMs);

            // 写入使用日志
            logUsageAsync(context, model, estimatedInputTokens, estimatedOutputTokens,
                    actualCost, null, (int) responseTimeMs, 200, null, true, httpRequest,
                    requestStartTime, requestEndTime, inputTextLength, content.length());

            log.info("[ChatCompletions] RoleChat 同步完成: contentLength={}, cost={}", content.length(), actualCost);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildSyncResponse(model, null, content, estimatedInputTokens, estimatedOutputTokens));

        } catch (Exception e) {
            log.error("[ChatCompletions] RoleChat 同步处理失败: {}", e.getMessage(), e);

            // 退还预扣配额
            quotaService.refundPreConsumed(context, preConsumed);

            // 写入错误日志
            long responseTimeMs = System.currentTimeMillis() - startTime;
            logUsageAsync(context, null, 0, 0, 0, null,
                    (int) responseTimeMs, 500, e.getMessage(), true, httpRequest,
                    requestStartTime, LocalDateTime.now(), inputTextLength, 0);

            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", Map.of(
                            "message", "角色聊天处理失败: " + e.getMessage(),
                            "type", "server_error"
                    )));
        }
    }

    /**
     * 处理普通 Agent 聊天请求（SSE 流式，原有逻辑不变）。
     */
    private SseEmitter handleAgentChatSse(JsonNode jsonNode, TokenAuthContext context, HttpServletRequest httpRequest) {
        LocalDateTime requestStartTime = LocalDateTime.now();

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        // 虚拟线程不继承 ThreadLocal:拍快照,在异步任务体首行恢复,让 MCP 工具拿到平台身份
        final PlatformUserContext.Snapshot platformSnapshot = PlatformUserContext.snapshot();

        executor.execute(() -> {
            PlatformUserContext.restore(platformSnapshot);
            // 预提取 request 字段:Tomcat 在 async 路径会回收 HttpServletRequest,
            // error/complete 回调里若再访问 httpRequest 会抛 "The request object has been recycled"
            final String clientIp = getClientIpAddress(httpRequest);
            final String requestPath = httpRequest != null ? httpRequest.getRequestURI() : null;
            final String requestMethod = httpRequest != null ? httpRequest.getMethod() : null;

            int preConsumed = 0;
            long startTime = System.currentTimeMillis();
            int inputTextLength = 0;
            AtomicInteger outputLen = new AtomicInteger(0);

            try {
                // 从请求体提取 roleInfo（可选）；流式路径不复用同步分支的路由
                RoleChatRequest.RoleInfo roleInfo = null;
                JsonNode roleInfoNode = jsonNode.get("roleInfo");
                if (roleInfoNode != null && !roleInfoNode.isNull()) {
                    try {
                        roleInfo = objectMapper.treeToValue(roleInfoNode, RoleChatRequest.RoleInfo.class);
                    } catch (Exception e) {
                        log.warn("[ChatCompletions] roleInfo 解析失败，忽略角色注入: {}", e.getMessage());
                    }
                }

                ChatCompletionRequest request = objectMapper.treeToValue(jsonNode, ChatCompletionRequest.class);
                inputTextLength = calculateInputTextLength(request.getMessages());

                // 检查模型是否允许
                if (context.isModelLimitsEnabled() && !context.isModelAllowed(request.getModel())) {
                    emitError(emitter, "模型 '" + request.getModel() + "' 未授权访问");
                    return;
                }

                // 预扣费
                preConsumed = quotaService.preConsumeQuota(context, request.getModel());
                if (preConsumed == 0) {
                    emitError(emitter, "配额不足");
                    return;
                }

                // 将 OpenAI 格式消息转为 Spring AI Message 列表(提前,供路由提取 userMessage)
                List<Message> messages = convertToSpringMessages(request.getMessages());

                // 动态加载分组 MCP 工具(接入六道防线:L2 路由收敛 + cid + 熔断 + onThinkingTurn),
                // 绑定当前请求的 emitter,使工具调用过程中的 image/confirmation 等事件能实时推送给前端
                String sessionId = "chatcmpl_" + context.getTokenId() + "_" + System.currentTimeMillis();
                String userMessage = extractLastUserMessage(messages);
                List<ToolCallback> groupTools = agentOrchestrator.getRoutedToolCallbacks(
                        userMessage, context.getGroupName(), emitter, sessionId);
                log.info("[ChatCompletions] AgentChat 路由收敛工具: groupName={}, toolCount={}, sessionId={}",
                        context.getGroupName(), groupTools.size(), sessionId);

                // Token 计数器
                AtomicInteger chunkCount = new AtomicInteger(0);

                // 平台用户会话持久化:JWT 认证后有 PlatformUserContext.userId,把本轮 user/assistant 消息落到归属用户的会话
                // (sk- 匿名/third-party 路径无 userId,保持无状态)
                String platformUserId = PlatformUserContext.getUserId();
                String persistSessionId = null;
                if (platformUserId != null && !platformUserId.isEmpty()) {
                    JsonNode sidNode = jsonNode.get("sessionId");
                    String clientSid = (sidNode != null && !sidNode.isNull()) ? sidNode.asText().trim() : "";
                    persistSessionId = resolvePersistSession(clientSid, platformUserId);
                    if (userMessage != null && !userMessage.isEmpty()) {
                        dialogManager.appendMessage(persistSessionId, MessageRole.USER, userMessage);
                    }
                }
                final String finalPersistSessionId = persistSessionId;
                final StringBuilder assistantText = new StringBuilder();

                int finalPreConsumed = preConsumed;
                int finalInputTextLength = inputTextLength;
                roleMessageService.buildAgentChatContext(
                        messages, context.getGroupName(), request.getModel(), groupTools, roleInfo)
                        .subscribe(
                                chunk -> {
                                    try {
                                        if (chunk != null && !chunk.isEmpty()) {
                                            // 诊断日志：记录每个 chunk 的到达时刻，用于判断是否真流式
                                            // （后端 chunk 若分散在数秒内 = 流式正常；若全部挤在同一毫秒 = 上游一次性返回）
                                            log.info("[ChatCompletions][stream] chunk#{} t={}ms len={}",
                                                    chunkCount.get() + 1,
                                                    System.currentTimeMillis() - startTime,
                                                    chunk.length());
                                            chunkCount.incrementAndGet();
                                            outputLen.addAndGet(chunk.length());
                                            assistantText.append(chunk);
                                            // 流式中间 chunk 的 finish_reason 必须为 null（OpenAI 协议）
                                            String delta = "data: " + buildSSEData(
                                                    request.getModel(), chunk, null) + "\n\n";
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(delta));
                                        }
                                    } catch (Exception e) {
                                        log.error("[ChatCompletions] SSE发送失败: {}", e.getMessage());
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    log.error("[ChatCompletions] 流式调用失败: {}", error.getMessage());
                                    quotaService.refundPreConsumed(context, finalPreConsumed);
                                    long responseTimeMs = System.currentTimeMillis() - startTime;
                                    logUsageAsync(context, request.getModel(), 0, 0, 0, null,
                                            (int) responseTimeMs, 500, error.getMessage(), true,
                                            clientIp, requestPath, requestMethod,
                                            requestStartTime, LocalDateTime.now(), finalInputTextLength, outputLen.get());
                                    emitError(emitter, error.getMessage());
                                },
                                () -> {
                                    try {
                                        long responseTimeMs = System.currentTimeMillis() - startTime;
                                        log.info("[ChatCompletions][stream] 流式完成: totalMs={} chunkCount={} outputLen={}",
                                                responseTimeMs, chunkCount.get(), outputLen.get());
                                        // 估算 Token 数
                                        int estimatedOutputTokens = estimateTokens(chunkCount.get() * 50);
                                        int estimatedInputTokens = estimateTokens(100);
                                        int actualCost = quotaService.postConsumeQuota(context, estimatedInputTokens,
                                                estimatedOutputTokens, request.getModel(), null, finalPreConsumed, responseTimeMs);

                                        logUsageAsync(context, request.getModel(), estimatedInputTokens, estimatedOutputTokens,
                                                actualCost, null, (int) responseTimeMs, 200, null, true,
                                                clientIp, requestPath, requestMethod,
                                                requestStartTime, LocalDateTime.now(), finalInputTextLength, outputLen.get());

                                        // 持久化本轮 assistant 回复到平台用户会话
                                        if (finalPersistSessionId != null && assistantText.length() > 0) {
                                            dialogManager.appendMessage(finalPersistSessionId, MessageRole.ASSISTANT, assistantText.toString());
                                        }
                                        // 结束 chunk：finish_reason=stop，空 delta（OpenAI 协议要求）
                                        emitter.send(SseEmitter.event()
                                                .name("message")
                                                .data("data: " + buildSSEData(request.getModel(), "", "stop") + "\n\n"));
                                        emitter.send(SseEmitter.event()
                                                .name("message")
                                                .data("data: [DONE]\n\n"));
                                        emitter.complete();
                                    } catch (Exception ignored) {
                                    }
                                }
                        );

            } catch (Exception e) {
                log.error("[ChatCompletions] AgentChat 处理失败: {}", e.getMessage(), e);
                quotaService.refundPreConsumed(context, preConsumed);
                emitError(emitter, "请求解析失败: " + e.getMessage());
            } finally {
                PlatformUserContext.clear();
            }
        });

        emitter.onTimeout(() -> log.warn("[ChatCompletions] SSE连接超时: maskedKey={}", context.getMaskedTokenKey()));
        emitter.onError(e -> log.warn("[ChatCompletions] SSE连接异常: {}", e.getMessage()));

        return emitter;
    }

    /**
     * 提取消息列表中最后一条 user 消息文本(供 L2 意图路由 detectAgent 用)。无则返回空串。
     */
    private String extractLastUserMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage um) {
                return um.getText();
            }
        }
        return "";
    }

    /**
     * 解析持久化用的会话 ID(平台用户会话):
     *  - 前端传了 sessionId 且库中无对应会话 → 用该 id 建归属当前用户的会话(前后端 id 一致,无需回传)
     *  - 已存在且归属当前用户(或无主) → 复用
     *  - 已存在但不归属(越权冒用)或未传 sessionId → 后端新建(生成新 id)
     */
    private String resolvePersistSession(String clientSessionId, String userId) {
        if (clientSessionId != null && !clientSessionId.isEmpty()) {
            Conversation existing = dialogManager.getSession(clientSessionId);
            if (existing == null) {
                return dialogManager.createUserSessionWithId(clientSessionId, userId);
            }
            if (existing.getUserId() == null || userId.equals(existing.getUserId())) {
                return clientSessionId;
            }
        }
        return dialogManager.createUserSession(null, userId);
    }

    /**
     * 同步 Chat Completions（非流式，独立路径）。
     */
    @PostMapping(value = "/chat/completions/sync", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chatCompletionsSync(@RequestBody ChatCompletionRequest request, HttpServletRequest httpRequest) {
        TokenAuthContext context = TokenAuthContext.get();
        Long tokenId = context.getTokenId();
        long startTime = System.currentTimeMillis();
        LocalDateTime requestStartTime = LocalDateTime.now();
        int inputTextLength = calculateInputTextLength(request.getMessages());

        log.info("[ChatCompletions] 同步模式: maskedKey={}, model={}",
                context.getMaskedTokenKey(), request.getModel());

        // 检查模型是否允许
        if (context.isModelLimitsEnabled() && !context.isModelAllowed(request.getModel())) {
            return Map.of("error", Map.of(
                    "message", "模型 '" + request.getModel() + "' 未授权访问",
                    "type", "invalid_request_error"
            ));
        }

        int preConsumed = quotaService.preConsumeQuota(context, request.getModel());
        if (preConsumed == 0) {
            return Map.of("error", Map.of(
                    "message", "配额不足",
                    "type", "quota_exceeded"
            ));
        }

        try {
            // 解析动态渠道
            resolveAndSetDynamicChannel(context.getGroupName(), request.getModel());

            String userMessage = extractUserMessage(request);
            String sessionId = "api_" + tokenId;
            ChatResponse response = agentOrchestrator.orchestrate(sessionId, userMessage, context.getGroupName());

            long responseTimeMs = System.currentTimeMillis() - startTime;
            String content = response.getContent();
            int estimatedInputTokens = estimateTokens(100);
            int estimatedOutputTokens = estimateTokens(content != null ? content.length() : 0);
            int actualCost = quotaService.postConsumeQuota(context, estimatedInputTokens, estimatedOutputTokens,
                    request.getModel(), null, preConsumed, responseTimeMs);

            int outputTextLength = content != null ? content.length() : 0;
            logUsageAsync(context, request.getModel(), estimatedInputTokens, estimatedOutputTokens,
                    actualCost, null, (int) responseTimeMs, 200, null, false, httpRequest,
                    requestStartTime, LocalDateTime.now(), inputTextLength, outputTextLength);

            return buildSyncResponse(request.getModel(), request.getMessages(), content,
                    estimatedInputTokens, estimatedOutputTokens);

        } catch (Exception e) {
            log.error("[ChatCompletions] 同步调用失败: {}", e.getMessage(), e);
            quotaService.refundPreConsumed(context, preConsumed);

            long responseTimeMs = System.currentTimeMillis() - startTime;
            logUsageAsync(context, request.getModel(), 0, 0, 0, null,
                    (int) responseTimeMs, 500, e.getMessage(), false, httpRequest,
                    requestStartTime, LocalDateTime.now(), inputTextLength, 0);

            return Map.of("error", Map.of(
                    "message", e.getMessage(),
                    "type", "server_error"
            ));
        } finally {
            DynamicChannelHolder.clear();
        }
    }

    /**
     * 应用渠道的模型名映射。
     */
    private String applyModelMapping(com.mmcove.agent.common.model.entity.AiChannel channel, String modelName) {
        String mapping = channel.getModelMapping();
        if (mapping == null || mapping.isEmpty()) {
            return modelName;
        }
        try {
            java.util.Map<String, String> mappingMap = objectMapper.readValue(mapping,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return mappingMap.getOrDefault(modelName, modelName);
        } catch (Exception e) {
            log.warn("[ChatCompletions] 解析模型映射失败: {}", e.getMessage());
            return modelName;
        }
    }

    /**
     * 估算 Token 数（简单估算：约 2 字符 = 1 Token）。
     */
    private int estimateTokens(int charCount) {
        return Math.max(1, charCount / 2);
    }

    /**
     * 异步写入使用日志。
     */
    private void logUsageAsync(TokenAuthContext ctx, String model, int inputTokens, int outputTokens,
                               int quotaUsed, Long channelId, int responseTimeMs, int statusCode,
                               String errorMessage, boolean isStream, HttpServletRequest httpRequest,
                               LocalDateTime requestStartTime, LocalDateTime requestEndTime,
                               int inputTextLength, int outputTextLength) {
        try {
            String clientIp = getClientIpAddress(httpRequest);
            String requestPath = httpRequest != null ? httpRequest.getRequestURI() : null;
            String requestMethod = httpRequest != null ? httpRequest.getMethod() : null;

            ApiTokenUsageLog log = UsageLogService.buildLog(
                    ctx.getTokenId(), ctx.getMaskedTokenKey(), model,
                    inputTokens, outputTokens, quotaUsed,
                    channelId, ctx.getUserId(), ctx.getGroupName(),
                    clientIp, requestPath, requestMethod,
                    responseTimeMs, statusCode, errorMessage, isStream,
                    requestStartTime, requestEndTime, inputTextLength, outputTextLength
            );
            usageLogService.logUsage(log);
        } catch (Exception e) {
            log.error("[ChatCompletions] 日志写入失败: {}", e.getMessage());
        }
    }

    /**
     * 异步写入使用日志(接收预提取的 request 字段,避免 async 回调访问已回收的 HttpServletRequest)。
     * <p>用于 SSE 流式路径:error/complete 回调执行时 Tomcat 可能已回收 request 对象,
     * 此时再调用 getRequestURI/getMethod/getHeader 会抛
     * "The request object has been recycled and is no longer associated with this facade"。
     * 因此在进入 async 线程时预提取 clientIp/path/method,回调里直接用预提取值。
     */
    private void logUsageAsync(TokenAuthContext ctx, String model, int inputTokens, int outputTokens,
                               int quotaUsed, Long channelId, int responseTimeMs, int statusCode,
                               String errorMessage, boolean isStream,
                               String clientIp, String requestPath, String requestMethod,
                               LocalDateTime requestStartTime, LocalDateTime requestEndTime,
                               int inputTextLength, int outputTextLength) {
        try {
            ApiTokenUsageLog log = UsageLogService.buildLog(
                    ctx.getTokenId(), ctx.getMaskedTokenKey(), model,
                    inputTokens, outputTokens, quotaUsed,
                    channelId, ctx.getUserId(), ctx.getGroupName(),
                    clientIp, requestPath, requestMethod,
                    responseTimeMs, statusCode, errorMessage, isStream,
                    requestStartTime, requestEndTime, inputTextLength, outputTextLength
            );
            usageLogService.logUsage(log);
        } catch (Exception e) {
            log.error("[ChatCompletions] 日志写入失败: {}", e.getMessage());
        }
    }

    /**
     * 获取真实客户端 IP（解析反向代理头）。
     * 按优先级：X-Forwarded-For → X-Real-IP → Proxy-Client-IP → WL-Proxy-Client-IP → getRemoteAddr()。
     * 处理多 IP 逗号分隔，取第一个非 unknown 的值。
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 多级代理时取第一个
                int commaIdx = ip.indexOf(',');
                if (commaIdx > 0) {
                    ip = ip.substring(0, commaIdx);
                }
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 计算请求中所有 messages 内容的字符总长度（兼容 ChatCompletionRequest.ChatMessage 列表）。
     */
    private int calculateInputTextLength(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage msg : messages) {
            String text = msg.getContentAsString();
            if (text != null) {
                total += text.length();
            }
        }
        return total;
    }

    /**
     * 计算 RoleChat 请求的输入文本长度（userAsk + messages 内容）。
     */
    private int calculateInputTextLength(RoleChatRequest request) {
        int total = 0;
        if (request.getUserAsk() != null) {
            total += request.getUserAsk().length();
        }
        if (request.getMessages() != null) {
            for (RoleChatRequest.RoleChatMessage msg : request.getMessages()) {
                String text = msg.getContentAsString();
                if (text != null) {
                    total += text.length();
                }
            }
        }
        return total;
    }

    /**
     * 解析动态渠道并设置到 DynamicChannelHolder。
     * 根据 token 的 groupName 和请求的 model 路由到具体渠道。
     */
    private void resolveAndSetDynamicChannel(String groupName, String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return;
        }
        if (groupName == null || groupName.isEmpty()) {
            groupName = "default";
        }

        try {
            ChannelLoadBalancer.SelectedChannel selected = channelLoadBalancer.selectChannel(groupName, modelName, 0);
            if (selected != null && selected.channel() != null) {
                String mappedModel = applyModelMapping(selected.channel(), selected.model());
                var chatModel = dynamicChatModelFactory.getOrCreate(selected.channel());
                DynamicChannelHolder.set(chatModel, mappedModel, selected.channel().getType());
                log.info("[ChatCompletions] 动态渠道已设置: groupId={}, model={}→{}, channelId={}, channelType={}",
                        groupName, selected.model(), mappedModel, selected.channel().getId(), selected.channel().getType());
            }
        } catch (Exception e) {
            log.warn("[ChatCompletions] 动态渠道解析失败，将使用默认渠道: {}", e.getMessage());
        }
    }

    /**
     * 从请求中提取用户消息。
     */
    private String extractUserMessage(ChatCompletionRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (int i = request.getMessages().size() - 1; i >= 0; i--) {
                ChatMessage msg = request.getMessages().get(i);
                if ("user".equalsIgnoreCase(msg.getRole())) {
                    return msg.getContentAsString();
                }
            }
            return request.getMessages().get(request.getMessages().size() - 1).getContentAsString();
        }
        return request.getPrompt();
    }

    /**
     * 将 OpenAI 兼容的 ChatMessage 列表转为 Spring AI Message 列表。
     * 按 role 分发到 SystemMessage / UserMessage / AssistantMessage，文本内容用 getContentAsString()。
     */
    private List<Message> convertToSpringMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return result;
        }
        for (ChatMessage msg : messages) {
            String role = msg.getRole() != null ? msg.getRole().toLowerCase() : "";
            String text = msg.getContentAsString();
            switch (role) {
                case "system" -> result.add(new SystemMessage(text != null ? text : ""));
                case "user" -> result.add(new UserMessage(text != null ? text : ""));
                case "assistant" -> result.add(new AssistantMessage(text != null ? text : ""));
                default -> log.warn("[ChatCompletions] 忽略未知角色消息: role={}", msg.getRole());
            }
        }
        return result;
    }


    /**
     * 构建 SSE 数据。
     * <p>注意：finish_reason 必须用 null 表示"未结束"，不能用 Map.of —— Map.of 不允许 null 值，
     * 否则会抛 NullPointerException（NPE 的 getMessage() 返回 null，日志表现为 "构建SSE数据失败: null"）。
     * 因此这里改用 HashMap，允许 finish_reason 取 null。
     */
    private String buildSSEData(String model, String content, String finishReason) {
        try {
            Map<String, Object> choice = new java.util.HashMap<>();
            choice.put("index", 0);
            choice.put("delta", Map.of("content", content));
            choice.put("finish_reason", finishReason);

            Map<String, Object> chunk = new java.util.HashMap<>();
            chunk.put("id", "chatcmpl-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            chunk.put("object", "chat.completion.chunk");
            chunk.put("created", System.currentTimeMillis() / 1000);
            chunk.put("model", model);
            chunk.put("choices", List.of(choice));
            return objectMapper.writeValueAsString(chunk);
        } catch (Exception e) {
            log.error("[ChatCompletions] 构建SSE数据失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 构建同步响应（OpenAI ChatCompletion 格式）。
     */
    private Map<String, Object> buildSyncResponse(String model, List<ChatMessage> messages, String content,
                                                   int inputTokens, int outputTokens) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", "chatcmpl-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        response.put("object", "chat.completion");
        response.put("created", System.currentTimeMillis() / 1000);
        response.put("model", model);
        response.put("choices", List.of(
                Map.of(
                        "index", 0,
                        "message", Map.of(
                                "role", "assistant",
                                "content", content != null ? content : ""
                        ),
                        "finish_reason", "stop"
                )
        ));
        response.put("usage", Map.of(
                "prompt_tokens", inputTokens,
                "completion_tokens", outputTokens,
                "total_tokens", inputTokens + outputTokens
        ));
        return response;
    }

    /**
     * 发送错误到 SSE。
     */
    private void emitError(SseEmitter emitter, String message) {
        try {
            Map<String, Object> error = Map.of(
                    "error", Map.of(
                            "message", message,
                            "type", "server_error"
                    )
            );
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("data: " + buildErrorData(error) + "\n\n"));
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private String buildErrorData(Map<String, Object> error) {
        try {
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ==================== 请求/响应 DTO ====================

    @lombok.Data
    public static class ChatCompletionRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private String stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Map<String, Object> extraParams;

        // 兼容简单 prompt 格式
        private String prompt;
    }

    @lombok.Data
    public static class ChatMessage {
        private String role;
        /**
         * 内容，支持两种格式：
         * - 字符串：纯文本
         * - List：多模态 [{"type":"text","text":"..."}, {"type":"image_url",...}]
         */
        private Object content;
        private String name;

        /** 获取文本内容 */
        public String getContentAsString() {
            if (content == null) return null;
            if (content instanceof String s) return s;
            if (content instanceof java.util.List<?> list && !list.isEmpty()) {
                for (Object item : list) {
                    if (item instanceof java.util.Map<?, ?> map && "text".equals(map.get("type"))) {
                        Object text = map.get("text");
                        return text != null ? text.toString() : null;
                    }
                }
                return list.get(0).toString();
            }
            return content.toString();
        }
    }
}
