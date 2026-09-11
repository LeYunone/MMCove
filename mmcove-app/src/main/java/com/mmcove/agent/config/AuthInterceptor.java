package com.mmcove.agent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.context.PlatformUserContext;
import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.context.UserSessionContext;
import com.mmcove.agent.common.enums.TokenErrorCode;
import com.mmcove.agent.common.exception.TokenAuthException;
import com.mmcove.agent.common.model.entity.ApiToken;
import com.mmcove.agent.common.model.entity.ApiUser;
import com.mmcove.agent.common.model.entity.SystemConfig;
import com.mmcove.agent.infra.persistence.mapper.SystemConfigMapper;
import com.mmcove.agent.infra.persistence.repository.ApiTokenRepository;
import com.mmcove.agent.infra.persistence.repository.ApiUserRepository;
import com.mmcove.agent.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 本地鉴权拦截器（自建用户体系）。
 *
 * <p>认证来源（按优先级）：
 * <ol>
 *   <li>本地 JWT（{@link JwtService} 签发，登录/注册获得）：任何端点带
 *       {@code Authorization: Bearer <jwt>} → 验签后加载 {@link ApiUser}，
 *       填 {@link UserSessionContext}（含角色）+ {@link PlatformUserContext} + 轻量 {@link TokenAuthContext}。</li>
 *   <li>API Token（{@code sk-xxx}）：{@code /v1/**} OpenAI 兼容端点 → 查 api_token（配额/状态校验）。</li>
 * </ol>
 *
 * <p>管理后台路径（/api/token、/api/ai-*、/api/dashboard、/api/system-config）要求 role &gt;= 10，否则 403。
 * 聊天路径（/api/chat）只认本地 JWT；OpenAI 兼容（/v1/chat/completions）可用 sk-token，或免认证模式（系统配置）。
 *
 * @author mmcove
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final ApiTokenRepository apiTokenRepository;
    private final ApiUserRepository apiUserRepository;
    private final SystemConfigMapper systemConfigMapper;
    private final JwtService jwtService;
    private final com.mmcove.agent.mcp.external.McpSessionIdentityHolder mcpSessionIdentityHolder;
    private final com.mmcove.agent.mcp.external.McpProductLineResolver mcpProductLineResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 1. 公开接口放行
        if (isPublicPath(path)) {
            return true;
        }

        String bearer = extractBearer(request);

        // 2. 本地 JWT：任何端点带了 JWT 即解析身份
        if (bearer != null && !bearer.startsWith("sk-") && looksLikeJwt(bearer)) {
            Claims claims;
            try {
                claims = jwtService.parseAndVerify(bearer);
            } catch (Exception e) {
                log.debug("[鉴权] JWT 校验失败: {}", e.getMessage());
                sendError(response, TokenErrorCode.AUTH_FAILED, "登录已过期，请重新登录");
                return false;
            }
            // refresh token 不用于接口鉴权
            if ("refresh".equals(claims.get("type"))) {
                sendError(response, TokenErrorCode.AUTH_FAILED, "请使用 access token");
                return false;
            }
            ApiUser user = apiUserRepository.findById(claims.getSubject()).orElse(null);
            if (user == null || !user.isEnabled()) {
                sendError(response, TokenErrorCode.AUTH_FAILED, "用户不可用");
                return false;
            }
            setupJwtContext(user);
            // 管理端门禁
            if (isAdminPath(path) && (user.getRole() == null || user.getRole() < ApiUser.ROLE_ADMIN_USER)) {
                clearContexts();
                sendError(response, TokenErrorCode.PERMISSION_DENIED, TokenErrorCode.PERMISSION_DENIED.getMessage());
                return false;
            }
            return true;
        }

        // 3. API Token 认证：/v1/** 与 /api/v1/**（第三方 OpenAI 兼容 sk- token）
        if (isApiPath(path)) {
            if (isChatCompletionsPath(path) && !isChatAuthRequired()) {
                log.debug("[鉴权] 聊天接口免认证模式，尝试使用 Token 分组路由");
                if (!trySetupTokenContextLenient(request)) {
                    setupAnonymousContext();
                }
                return true;
            }
            return handleApiAuthentication(request, response);
        }

        // 3.5 MCP Server SSE 端点(/sse、/mcp/message)：外部 AI 工具接入,只认 sk- token。
        //     默认产品线身份 = X-Product-Line 请求头(各自在 MCP 客户端配置自己的产品线);
        //     POST /mcp/message 认证通过后捕获 sessionId→产品线,由 McpSessionIdentityHolder
        //     桥接到会话内部线程(MCP SDK 不在 servlet 线程执行工具,ThreadLocal 读不到)。
        if (isMcpPath(path)) {
            boolean passed = handleApiAuthentication(request, response);
            if (passed) {
                String headerLine = request.getHeader("X-Product-Line");
                if (headerLine != null && !headerLine.isBlank()) {
                    try {
                        TokenAuthContext.get().setProductLineId(
                                mcpProductLineResolver.resolve(headerLine.trim()).getId());
                    } catch (IllegalArgumentException e) {
                        sendError(response, TokenErrorCode.AUTH_FAILED,
                                "X-Product-Line 配置错误: " + e.getMessage());
                        return false;
                    }
                }
                String sessionId = request.getParameter("sessionId");
                if (sessionId != null && !sessionId.isBlank()) {
                    mcpSessionIdentityHolder.capture(sessionId,
                            TokenAuthContext.get().getProductLineId(), TokenAuthContext.get().getTokenId());
                }
            }
            return passed;
        }

        // 4. 管理后台 / 聊天路径：只认本地 JWT（上方分支），无 JWT 直接 401
        if (isChatPath(path) || isAdminPath(path)) {
            sendError(response, TokenErrorCode.AUTH_FAILED, "请先登录");
            return false;
        }

        // 其他路径不拦截
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        clearContexts();
    }

    // ==================== 公开/路径判定 ====================

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/actuator")
                || path.startsWith("/flux-images/")
                || path.equals("/");
    }

    /** MCP Server SSE 端点(外部 AI 工具接入通道) */
    private boolean isMcpPath(String path) {
        return "/sse".equals(path) || "/mcp/message".equals(path);
    }

    private boolean isApiPath(String path) {
        return path.startsWith("/v1/") || path.startsWith("/api/v1/");
    }

    private boolean isAdminPath(String path) {
        return path.startsWith("/api/token")
                || path.startsWith("/api/ai-role")
                || path.startsWith("/api/ai-channel")
                || path.startsWith("/api/ai-role-channel")
                || path.startsWith("/api/ai-ability")
                || path.startsWith("/api/dashboard")
                || path.startsWith("/api/system-config")
                || path.startsWith("/api/tool-definitions")
                || path.startsWith("/api/agents")
                || path.startsWith("/api/response-templates")
                || path.startsWith("/api/knowledge-bases")
                || path.startsWith("/api/product-lines")
                || path.startsWith("/api/vibe");
    }

    private boolean isChatPath(String path) {
        return path.startsWith("/api/chat") || path.startsWith("/api/sessions");
    }

    private boolean isChatCompletionsPath(String path) {
        return path.equals("/v1/chat/completions")
                || path.equals("/v1/chat/completions/sync")
                || path.startsWith("/api/v1/chat/completions");
    }

    private boolean isChatAuthRequired() {
        try {
            LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemConfig::getConfigKey, SystemConfig.KEY_CHAT_AUTH_REQUIRED);
            SystemConfig config = systemConfigMapper.selectOne(wrapper);
            if (config != null) {
                return !"0".equals(config.getConfigValue());
            }
        } catch (Exception e) {
            log.debug("[鉴权] 读取认证开关失败，默认需要认证: {}", e.getMessage());
        }
        return true;
    }

    // ==================== JWT 上下文 ====================

    /** 本地 JWT 认证通过：填 UserSessionContext + PlatformUserContext + 轻量 TokenAuthContext。
     *  一人一个 token：按 userId 查启用 sk-token 用于配额/渠道路由；查不到则匿名兜底。 */
    private void setupJwtContext(ApiUser user) {
        UserSessionContext usc = UserSessionContext.get();
        usc.setUserId(user.getId());
        usc.setUsername(user.getUsername());
        usc.setRole(user.getRole());

        PlatformUserContext.setUserId(user.getId());

        Optional<ApiToken> userToken = apiTokenRepository.findActiveByUserId(user.getId());
        if (userToken.isPresent()) {
            applyUserTokenContext(userToken.get(), user.getId(), user.getUsername());
            log.info("[鉴权] JWT 命中: userId={}, tokenId={}", user.getId(), userToken.get().getId());
        } else {
            setupAnonymousContext();
            TokenAuthContext.get().setUserId(user.getId());
            TokenAuthContext.get().setUsername(user.getUsername());
            log.info("[鉴权] JWT 命中(无 token，匿名兜底): userId={}", user.getId());
        }
    }

    private void applyUserTokenContext(ApiToken token, String userId, String username) {
        TokenAuthContext ctx = TokenAuthContext.get();
        ctx.setTokenKey(token.getTokenKey());
        ctx.setTokenId(token.getId());
        ctx.setTokenName(token.getName());
        ctx.setUserId(userId);
        ctx.setUsername(username != null ? username : userId);
        ctx.setUnlimitedQuota(token.getUnlimitedQuota() != null && token.getUnlimitedQuota());
        ctx.setRemainQuota(token.getRemainQuota());
        ctx.setUsedQuota(token.getUsedQuota() != null ? token.getUsedQuota() : 0);
        String groupName = token.getGroupName();
        ctx.setGroupName((groupName != null && !groupName.isEmpty()) ? groupName : "default");
        ctx.setModelLimitsEnabled(token.getModelLimitsEnabled() != null && token.getModelLimitsEnabled());
        ctx.setModelLimits(token.getModelLimits());
    }

    // ==================== API Token（sk-）认证 ====================

    private boolean handleApiAuthentication(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String tokenKey = extractTokenKey(request);
        if (!StringUtils.hasText(tokenKey)) {
            sendError(response, TokenErrorCode.AUTH_FAILED, "未提供有效的Token");
            return false;
        }
        try {
            validateAndSetTokenContext(tokenKey);
            return true;
        } catch (TokenAuthException e) {
            sendError(response, e.getErrorCode(), e.getMessage());
            return false;
        }
    }

    private void validateAndSetTokenContext(String tokenKey) {
        Optional<ApiToken> tokenOpt = apiTokenRepository.findByKey(tokenKey);
        if (tokenOpt.isEmpty()) {
            log.warn("[鉴权] Token不存在: {}", maskKey(tokenKey));
            throw new TokenAuthException(TokenErrorCode.TOKEN_NOT_FOUND);
        }
        ApiToken token = tokenOpt.get();
        if (token.getStatus() == null || token.getStatus() != ApiToken.STATUS_ENABLED) {
            log.warn("[鉴权] Token状态不可用: id={}, status={}", token.getId(), token.getStatus());
            if (token.isPending()) {
                throw new TokenAuthException(TokenErrorCode.TOKEN_PENDING);
            }
            if (token.isRejected()) {
                throw new TokenAuthException(TokenErrorCode.TOKEN_REJECTED);
            }
            throw new TokenAuthException(TokenErrorCode.TOKEN_DISABLED);
        }
        if (token.isExpired()) {
            log.warn("[鉴权] Token已过期: id={}", token.getId());
            throw new TokenAuthException(TokenErrorCode.TOKEN_EXPIRED);
        }
        if (!token.hasQuota()) {
            log.warn("[鉴权] Token额度已用尽: id={}", token.getId());
            throw new TokenAuthException(TokenErrorCode.TOKEN_EXHAUSTED);
        }
        Optional<ApiUser> userOpt = apiUserRepository.findById(token.getUserId());
        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            log.warn("[鉴权] Token关联用户不可用: tokenId={}, userId={}", token.getId(), token.getUserId());
            throw new TokenAuthException(TokenErrorCode.USER_DISABLED);
        }
        apiTokenRepository.updateAccessedTime(token.getId());
        TokenAuthContext ctx = TokenAuthContext.get();
        ctx.setTokenKey(tokenKey);
        ctx.setTokenId(token.getId());
        ctx.setTokenName(token.getName());
        ctx.setUserId(token.getUserId());
        ctx.setUsername(userOpt.get().getUsername());
        ctx.setUnlimitedQuota(token.getUnlimitedQuota() != null && token.getUnlimitedQuota());
        ctx.setRemainQuota(token.getRemainQuota());
        ctx.setUsedQuota(token.getUsedQuota() != null ? token.getUsedQuota() : 0);
        String groupName = token.getGroupName();
        ctx.setGroupName((groupName != null && !groupName.isEmpty()) ? groupName : "default");
        ctx.setModelLimitsEnabled(token.getModelLimitsEnabled() != null && token.getModelLimitsEnabled());
        ctx.setModelLimits(token.getModelLimits());
        // 同步用户会话上下文
        UserSessionContext usc = UserSessionContext.get();
        usc.setUserId(token.getUserId());
        usc.setUsername(userOpt.get().getUsername());
        usc.setRole(userOpt.get().getRole());
        PlatformUserContext.setUserId(token.getUserId());
        log.debug("[鉴权] sk-Token 认证成功: tokenId={}, maskedKey={}", token.getId(), ctx.getMaskedTokenKey());
    }

    private boolean trySetupTokenContextLenient(HttpServletRequest request) {
        String tokenKey = extractTokenKey(request);
        if (!StringUtils.hasText(tokenKey)) {
            return false;
        }
        try {
            Optional<ApiToken> tokenOpt = apiTokenRepository.findByKey(tokenKey);
            if (tokenOpt.isEmpty()) {
                return false;
            }
            ApiToken token = tokenOpt.get();
            TokenAuthContext ctx = TokenAuthContext.get();
            ctx.setTokenKey(tokenKey);
            ctx.setTokenId(token.getId());
            ctx.setTokenName(token.getName());
            ctx.setUserId(token.getUserId());
            ctx.setUsername("anonymous");
            ctx.setUnlimitedQuota(true);
            ctx.setRemainQuota(Integer.MAX_VALUE);
            ctx.setUsedQuota(0);
            String groupName = token.getGroupName();
            ctx.setGroupName((groupName != null && !groupName.isEmpty()) ? groupName : "default");
            ctx.setModelLimitsEnabled(false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void setupAnonymousContext() {
        TokenAuthContext ctx = TokenAuthContext.get();
        ctx.setTokenKey("anonymous");
        ctx.setTokenId(null);
        ctx.setTokenName("匿名访问");
        ctx.setUserId("anonymous");
        ctx.setUsername("anonymous");
        ctx.setUnlimitedQuota(true);
        ctx.setRemainQuota(Integer.MAX_VALUE);
        ctx.setUsedQuota(0);
        ctx.setGroupName("default");
        ctx.setModelLimitsEnabled(false);
    }

    // ==================== 工具方法 ====================

    private static String extractBearer(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader)) {
            return null;
        }
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private static String extractTokenKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader)) {
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7).trim();
            }
            return authHeader.trim();
        }
        String wsProtocol = request.getHeader("Sec-WebSocket-Protocol");
        if (StringUtils.hasText(wsProtocol)) {
            String[] parts = wsProtocol.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("openai-insecure-api-key.")) {
                    return part.substring("openai-insecure-api-key.".length()).trim();
                }
                if (part.startsWith("sk-")) {
                    return part;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeJwt(String token) {
        if (token == null || token.length() < 16) {
            return false;
        }
        int dots = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '.') {
                dots++;
            }
        }
        return dots == 2;
    }

    private void sendError(HttpServletResponse response, TokenErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", errorCode.getHttpStatus());
        errorBody.put("message", message);
        errorBody.put("data", null);
        response.getWriter().write(new ObjectMapper().writeValueAsString(errorBody));
    }

    private static String maskKey(String key) {
        if (key == null || key.length() < 10) {
            return "***";
        }
        return key.substring(0, 6) + "***" + key.substring(key.length() - 4);
    }

    private void clearContexts() {
        TokenAuthContext.clear();
        UserSessionContext.clear();
        PlatformUserContext.clear();
    }
}
