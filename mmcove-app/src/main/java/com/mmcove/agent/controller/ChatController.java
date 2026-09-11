package com.mmcove.agent.controller;

import com.mmcove.agent.common.context.PlatformUserContext;
import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.context.UserSessionContext;
import com.mmcove.agent.common.enums.SseEventType;
import com.mmcove.agent.common.enums.TokenErrorCode;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.dto.ChatRequest;
import com.mmcove.agent.common.model.dto.ChatResponse;
import com.mmcove.agent.common.model.entity.ApiToken;
import com.mmcove.agent.common.model.entity.ApiUser;
import com.mmcove.agent.core.orchestrator.AgentOrchestrator;
import com.mmcove.agent.infra.persistence.repository.ApiTokenRepository;
import com.mmcove.agent.infra.persistence.repository.ApiUserRepository;
import com.mmcove.agent.infra.sse.EventReplayService;
import com.mmcove.agent.infra.sse.SseEventStore;
import com.mmcove.agent.llm.gateway.DynamicChannelHolder;
import com.mmcove.agent.service.ChannelLoadBalancer;
import com.mmcove.agent.service.ChatQuotaService;
import com.mmcove.agent.service.DynamicChatModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * 聊天控制器，提供同步和 SSE 流式对话接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AgentOrchestrator agentOrchestrator;
    private final SseEventStore sseEventStore;
    private final EventReplayService eventReplayService;
    private final ExecutorService executor;
    private final ChatQuotaService chatQuotaService;
    private final ApiTokenRepository apiTokenRepository;
    private final ApiUserRepository apiUserRepository;
    private final ChannelLoadBalancer channelLoadBalancer;
    private final DynamicChatModelFactory dynamicChatModelFactory;

    /**
     * 同步对话。
     */
    @PostMapping
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse response = agentOrchestrator.orchestrate(
                request.getSessionId(),
                request.getUserMessage()
        );
        return ApiResponse.success(response);
    }

    /**
     * SSE 流式对话（增强版：含思维链事件 + 任务拆分 + 事件持久化）。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) String model) {

        // 1. 检查认证方式和配额
        TokenAuthContext tokenContext = TokenAuthContext.get();
        UserSessionContext sessionContext = UserSessionContext.get();
        boolean useApiToken = tokenContext.getTokenId() != null;

        if (useApiToken) {
            // api-token 模式：配额已在拦截器中检查
            log.info("[聊天] api-token模式: tokenId={}", tokenContext.getTokenId());
        } else {
            // 会话模式：检查每日免费限额，用 userId 作为唯一标识
            String userId = sessionContext.getUserId();
            if (!chatQuotaService.checkAndIncrementDailyLimit(userId)) {
                SseEmitter emitter = new SseEmitter();
                try {
                    sseEventStore.emitSimpleEvent(emitter, sessionId,
                            SseEventType.ERROR, TokenErrorCode.DAILY_LIMIT_EXCEEDED.getMessage());
                    emitter.complete();
                } catch (Exception ignored) {
                }
                return emitter;
            }
            log.info("[聊天] 免费模式: userId={}, 今日已用{}次", userId,
                    chatQuotaService.getDailyUsed(userId));
        }

        // 2. 解析渠道路由（API Token 模式使用 token 的 groupName）
        String groupName = "default";
        if (useApiToken && tokenContext.getGroupName() != null && !tokenContext.getGroupName().isEmpty()) {
            groupName = tokenContext.getGroupName();
        }

        // 如果指定了 model，解析动态渠道
        if (model != null && !model.isEmpty()) {
            resolveAndSetDynamicChannel(groupName, model);
        }

        // 3. 执行流式对话
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        final String finalGroupName = groupName;
        // 虚拟线程不继承 ThreadLocal:拍快照,在异步任务体首行恢复,让 MCP 工具拿到平台身份
        final PlatformUserContext.Snapshot platformSnapshot = PlatformUserContext.snapshot();

        executor.execute(() -> {
            PlatformUserContext.restore(platformSnapshot);
            try {
                agentOrchestrator.orchestrateStream(sessionId, message, emitter, finalGroupName)
                        .subscribe(
                                chunk -> {
                                    try {
                                        if (chunk.getContent() != null && !chunk.getContent().isEmpty()) {
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(chunk.getContent()));
                                        }
                                    } catch (Exception e) {
                                        log.error("SSE 发送失败: {}", e.getMessage());
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    log.error("流式调用失败: {}", error.getMessage());
                                    try {
                                        sseEventStore.emitSimpleEvent(emitter, sessionId,
                                                SseEventType.ERROR, error.getMessage());
                                    } catch (Exception ignored) {
                                    }
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    try {
                                        // api-token 模式扣减配额
                                        if (useApiToken) {
                                            apiTokenRepository.decreaseQuota(tokenContext.getTokenId(), 1);
                                        }
                                        sseEventStore.emitSimpleEvent(emitter, sessionId,
                                                SseEventType.DONE, "[DONE]");
                                    } catch (Exception ignored) {
                                    } finally {
                                        DynamicChannelHolder.clear();
                                    }
                                    emitter.complete();
                                }
                        );
            } catch (Exception e) {
                log.error("SSE 处理异常: {}", e.getMessage());
                emitter.completeWithError(e);
            } finally {
                DynamicChannelHolder.clear();
                PlatformUserContext.clear();
            }
        });

        emitter.onTimeout(() -> log.warn("SSE 连接超时: sessionId={}", sessionId));
        emitter.onError(e -> log.warn("SSE 连接异常: {}", e.getMessage()));

        return emitter;
    }

    /**
     * 获取当前用户配额信息。
     * Token 模式下直接从 DB 读取最新配额，避免 ThreadLocal 上下文值不准确。
     */
    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> getQuota() {
        TokenAuthContext tokenContext = TokenAuthContext.get();
        UserSessionContext sessionContext = UserSessionContext.get();

        // Token 模式：从 DB 直接读取最新的配额数据
        if (tokenContext.getTokenId() != null) {
            Optional<ApiToken> tokenOpt = apiTokenRepository.findById(tokenContext.getTokenId());
            if (tokenOpt.isPresent()) {
                ApiToken token = tokenOpt.get();
                Map<String, Object> info = new java.util.LinkedHashMap<>();
                info.put("mode", "token");
                info.put("tokenId", token.getId());
                info.put("tokenName", token.getName());
                int used = token.getUsedQuota() != null ? token.getUsedQuota() : 0;
                int remain = token.getRemainQuota() != null ? token.getRemainQuota() : 0;
                if (token.getUnlimitedQuota() != null && token.getUnlimitedQuota()) {
                    info.put("used", used);
                    info.put("limit", -1);
                    info.put("unlimited", true);
                } else {
                    info.put("used", used);
                    info.put("limit", used + remain);
                    info.put("remain", remain);
                    info.put("unlimited", false);
                }
                log.debug("[配额查询] Token模式: tokenId={}, remain={}, used={}", token.getId(), remain, used);
                return ApiResponse.success(info);
            }
        }

        // 免费模式：使用 Redis 每日计数
        String userId = sessionContext.getUserId();
        Map<String, Object> quotaInfo = chatQuotaService.getQuotaInfo(userId, tokenContext);
        return ApiResponse.success(quotaInfo);
    }

    /**
     * 校验 API Token 是否可用，并返回其配额信息。
     * POST /api/chat/token-verify
     * 使用 认证，用户在聊天界面输入 token 后调用此接口验证。
     */
    @PostMapping("/token-verify")
    public ApiResponse<Map<String, Object>> verifyToken(@RequestBody Map<String, String> body) {
        String tokenKey = body.get("tokenKey");
        if (tokenKey == null || tokenKey.isEmpty()) {
            return ApiResponse.error(400, "请输入Token");
        }

        // 去掉 Bearer 前缀（如果有）
        if (tokenKey.startsWith("Bearer ")) {
            tokenKey = tokenKey.substring(7).trim();
        }

        Optional<ApiToken> tokenOpt = apiTokenRepository.findByKey(tokenKey);
        if (tokenOpt.isEmpty()) {
            return ApiResponse.error(404, "Token不存在");
        }

        ApiToken token = tokenOpt.get();

        // 校验归属：Token必须属于当前登录用户
        UserSessionContext sessionContext = UserSessionContext.get();
        String currentUserId = sessionContext.getUserId();
        if (currentUserId != null && !currentUserId.equals(token.getUserId())) {
            return ApiResponse.error(403, "该Token不属于当前登录用户");
        }

        // 检查状态
        if (token.isPending()) {
            return ApiResponse.error(400, "Token正在审核中，请等待管理员审批");
        }
        if (token.isRejected()) {
            return ApiResponse.error(400, "Token申请已被拒绝，请重新申请");
        }
        if (token.getStatus() == null || token.getStatus() != ApiToken.STATUS_ENABLED) {
            return ApiResponse.error(400, "Token已被禁用");
        }
        if (token.isExpired()) {
            return ApiResponse.error(400, "Token已过期");
        }
        if (!token.hasQuota()) {
            return ApiResponse.error(400, "Token配额已用尽");
        }

        // 校验通过，返回配额信息
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", token.getId());
        result.put("name", token.getName());
        result.put("unlimited", token.getUnlimitedQuota() != null && token.getUnlimitedQuota());
        result.put("remainQuota", token.getRemainQuota());
        result.put("usedQuota", token.getUsedQuota());

        return ApiResponse.success(result);
    }

    /**
     * 用户申请 API Token。
     * POST /api/chat/token-apply
     * 使用 认证（X-Token），自动关联或创建 ApiUser。
     */
    @PostMapping("/token-apply")
    public ApiResponse<Map<String, Object>> applyToken(@RequestBody TokenApplyRequest request) {
        UserSessionContext sessionContext = UserSessionContext.get();
        String username = sessionContext.getUsername();
        if (username == null || username.isEmpty()) {
            return ApiResponse.error(401, "请先登录");
        }

        // 获取用户ID，直接用 userId 作为用户唯一标识
        String userId = sessionContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            return ApiResponse.error(401, "无法获取用户身份");
        }

        // 同步创建 api_user 记录（id = userId），用于配额管理等
        ApiUser user = apiUserRepository.findById(userId).orElse(null);
        if (user == null) {
            user = new ApiUser();
            user.setId(userId);
            user.setUsername(username);
            user.setPassword(null);
            user.setRole(ApiUser.ROLE_COMMON_USER);
            user.setStatus(ApiUser.STATUS_ENABLED);
            user.setQuota(0L);
            apiUserRepository.insert(user);
            log.info("[Token申请] 自动创建ApiUser: username={}, userId={}", username, userId);
        }

        // 一人一个 token:已有启用 Token 则拒绝(如需更换请先删除)
        if (apiTokenRepository.findActiveByUserId(userId).isPresent()) {
            return ApiResponse.error(400, "每个用户仅可拥有一个有效 Token,如需更换请先删除现有 Token");
        }

        if (request.getName() != null && request.getName().length() > 30) {
            return ApiResponse.error(400, "令牌名称过长");
        }

        // 生成 Token Key
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String key = "sk-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ApiToken token = new ApiToken();
        token.setUserId(userId);
        token.setTokenKey(key);
        token.setName(request.getName() != null ? request.getName() : "未命名Token");
        token.setStatus(ApiToken.STATUS_PENDING);
        token.setCreatedTime(System.currentTimeMillis() / 1000);
        token.setAccessedTime(System.currentTimeMillis() / 1000);
        token.setExpiredTime(request.getExpiredTime() != null ? request.getExpiredTime() : -1);
        token.setRemainQuota(request.getRemainQuota() != null ? request.getRemainQuota() : 0);
        token.setUsedQuota(0);
        token.setUnlimitedQuota(request.getUnlimitedQuota() != null && request.getUnlimitedQuota());
        token.setModelLimitsEnabled(false);
        token.setModelLimits(null);
        token.setAllowIps(null);
        token.setGroupName(request.getGroup());
        token.setApplyReason(request.getApplyReason() != null ? request.getApplyReason() : "");

        apiTokenRepository.insert(token);

        log.info("[Token申请] 用户申请Token: username={}, tokenId={}, status=待审核", username, token.getId());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", token.getId());
        result.put("tokenKey", key);
        result.put("name", token.getName());
        result.put("unlimitedQuota", token.getUnlimitedQuota());
        result.put("remainQuota", token.getRemainQuota());
        result.put("expiredTime", token.getExpiredTime());
        result.put("status", token.getStatus());

        return ApiResponse.success(result);
    }

    // ==================== 内部方法 ====================

    /**
     * 解析动态渠道并设置到 DynamicChannelHolder。
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
                log.info("[Chat] 动态渠道已设置: groupId={}, model={}→{}, channelId={}",
                        groupName, selected.model(), mappedModel, selected.channel().getId());
            }
        } catch (Exception e) {
            log.warn("[Chat] 动态渠道解析失败，将使用默认渠道: {}", e.getMessage());
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
            java.util.Map<String, String> mappingMap = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(mapping, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return mappingMap.getOrDefault(modelName, modelName);
        } catch (Exception e) {
            log.warn("[Chat] 解析模型映射失败: {}", e.getMessage());
            return modelName;
        }
    }

    // ==================== 请求DTO ====================

    @lombok.Data
    public static class TokenApplyRequest {
        private String name;
        private Long expiredTime;
        private Integer remainQuota;
        private Boolean unlimitedQuota;
        private String group;
        private String applyReason;
    }

    /**
     * SSE 事件重放端点。
     */
    @GetMapping(value = "/stream/replay", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter replayEvents(
            @RequestParam String sessionId,
            @RequestParam(required = false) String lastEventId) {

        SseEmitter emitter = new SseEmitter(60 * 1000L);

        executor.execute(() -> {
            try {
                eventReplayService.replay(emitter, sessionId, lastEventId);
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 事件重放失败: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> log.warn("SSE 重放超时: sessionId={}", sessionId));
        emitter.onError(e -> log.warn("SSE 重放异常: {}", e.getMessage()));

        return emitter;
    }
}
