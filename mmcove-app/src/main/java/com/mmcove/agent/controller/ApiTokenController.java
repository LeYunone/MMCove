package com.mmcove.agent.controller;

import com.mmcove.agent.common.context.UserSessionContext;
import com.mmcove.agent.common.enums.TokenErrorCode;
import com.mmcove.agent.common.exception.TokenAuthException;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.ApiToken;
import com.mmcove.agent.common.model.entity.ApiUser;
import com.mmcove.agent.common.model.entity.SystemConfig;
import com.mmcove.agent.infra.persistence.mapper.SystemConfigMapper;
import com.mmcove.agent.infra.persistence.repository.ApiTokenRepository;
import com.mmcove.agent.infra.persistence.repository.ApiUserRepository;
import com.mmcove.agent.service.UsageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Token 管理控制器。
 * 提供Token的增删改查功能，兼容 new-api 接口路径。
 */
@Slf4j
@RestController
@RequestMapping("/api/token")
@RequiredArgsConstructor
public class ApiTokenController {

    private final ApiTokenRepository apiTokenRepository;
    private final ApiUserRepository apiUserRepository;
    private final SystemConfigMapper systemConfigMapper;
    private final UsageLogService usageLogService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 获取当前用户的所有Token列表。
     * GET /api/token
     */
    @GetMapping
    public ApiResponse<List<ApiToken>> listTokens(
            @RequestParam(required = false) Integer p,
            @RequestParam(required = false) Integer size) {

        String userId = getCurrentUserId();
        int page = p != null && p >= 0 ? p : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, 100) : 10;

        List<ApiToken> tokens = apiTokenRepository.findByUserIdPaged(userId, page * pageSize, pageSize);
        return ApiResponse.success(tokens);
    }

    /**
     * 管理员：获取所有用户的Token列表。
     * GET /api/token/admin
     */
    @GetMapping("/admin")
    public ApiResponse<List<ApiToken>> listAllTokens(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer p,
            @RequestParam(required = false) Integer size) {

        checkAdminPermission();

        int page = p != null && p >= 0 ? p : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, 100) : 10;

        List<ApiToken> tokens;
        if (userId != null) {
            // 按用户ID查询
            tokens = apiTokenRepository.findByUserIdPaged(userId, page * pageSize, pageSize);
        } else if (username != null) {
            // 按用户名查询
            ApiUser user = apiUserRepository.findByUsername(username).get();
            if (user == null) {
                return ApiResponse.error(404, "用户不存在");
            }
            tokens = apiTokenRepository.findByUserIdPaged(user.getId(), page * pageSize, pageSize);
        } else {
            // 搜索所有Token
            tokens = apiTokenRepository.search(null, keyword, null);
            // 分页处理
            int total = tokens.size();
            int start = page * pageSize;
            if (start > total) {
                tokens = List.of();
            } else {
                int end = Math.min(start + pageSize, total);
                tokens = tokens.subList(start, end);
            }
        }

        return ApiResponse.success(tokens);
    }

    /**
     * 搜索Token。
     * GET /api/token/search?keyword=xxx&token=xxx
     */
    @GetMapping("/search")
    public ApiResponse<List<ApiToken>> searchTokens(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String token) {

        String userId = getCurrentUserId();
        String keyPrefix = token;
        if (keyPrefix != null && keyPrefix.startsWith("sk-")) {
            keyPrefix = keyPrefix.substring(3);
        }

        List<ApiToken> tokens = apiTokenRepository.search(userId, keyword, keyPrefix);
        return ApiResponse.success(tokens);
    }

    /**
     * 获取指定Token详情。
     * GET /api/token/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<ApiToken> getToken(@PathVariable Long id) {
        String userId = getCurrentUserId();

        return apiTokenRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "Token不存在"));
    }

    /**
     * 获取Token状态（配额信息）。
     * GET /api/token/{id}/status
     */
    @GetMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> getTokenStatus(@PathVariable Long id) {
        String userId = getCurrentUserId();

        ApiToken token = apiTokenRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new TokenAuthException(TokenErrorCode.TOKEN_NOT_FOUND));

        Map<String, Object> status = new HashMap<>();
        status.put("object", "credit_summary");
        status.put("total_granted", token.getRemainQuota());
        status.put("total_used", token.getUsedQuota());
        status.put("total_available", token.getRemainQuota());
        status.put("expires_at", token.getExpiredTime() == -1 ? 0 : token.getExpiredTime() * 1000);

        return ApiResponse.success(status);
    }

    /**
     * 管理员：获取指定Token的使用统计。
     * GET /api/token/{id}/stats
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> getTokenStats(@PathVariable Long id) {
        checkAdminPermission();
        return ApiResponse.success(usageLogService.getTokenAggregatedStats(id));
    }

    /**
     * 创建新Token。
     * POST /api/token
     */
    @PostMapping
    public ApiResponse<ApiToken> createToken(@RequestBody CreateTokenRequest request) {
        String userId = getCurrentUserId();

        if (request.getName() != null && request.getName().length() > 30) {
            return ApiResponse.error(400, "令牌名称过长");
        }

        // 检查用户创建的Token数量限制
        List<ApiToken> userTokens = apiTokenRepository.findByUserId(userId);
        if (userTokens.size() >= 10) {
            return ApiResponse.error(400, "每个用户最多只能创建10个Token");
        }

        // 生成Token Key
        String key = generateTokenKey();

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
        token.setModelLimitsEnabled(request.getModelLimitsEnabled() != null && request.getModelLimitsEnabled());
        token.setModelLimits(request.getModelLimits());
        token.setAllowIps(request.getAllowIps());
        token.setGroupName(resolveGroupName(request.getGroup()));
        token.setApplyReason(request.getApplyReason() != null ? request.getApplyReason() : "");

        apiTokenRepository.insert(token);

        log.info("[Token管理] 申请Token: userId={}, tokenId={}, status=待审核", userId, token.getId());
        return ApiResponse.success(token);
    }

    /**
     快速创建Token（简化版）。
     * POST /api/token/quick
     */
    @PostMapping("/quick")
    public ApiResponse<Map<String, Object>> createTokenQuick(
            @RequestParam(defaultValue = "未命名Token") String name,
            @RequestParam(required = false) Long expiredTime,
            @RequestParam(required = false) Integer remainQuota) {
        String userId = getCurrentUserId();

        // 检查用户创建的Token数量限制
        List<ApiToken> userTokens = apiTokenRepository.findByUserId(userId);
        if (userTokens.size() >= 10) {
            return ApiResponse.error(400, "每个用户最多只能创建10个Token");
        }

        // 生成Token Key
        String key = generateTokenKey();

        ApiToken token = new ApiToken();
        token.setUserId(userId);
        token.setTokenKey(key);
        token.setName(name.length() > 30 ? name.substring(0, 30) : name);
        token.setStatus(ApiToken.STATUS_PENDING);
        token.setCreatedTime(System.currentTimeMillis() / 1000);
        token.setAccessedTime(System.currentTimeMillis() / 1000);
        token.setExpiredTime(expiredTime != null ? expiredTime : -1);
        token.setRemainQuota(remainQuota != null ? remainQuota : 0);
        token.setUsedQuota(0);
        token.setUnlimitedQuota(remainQuota == null);
        token.setModelLimitsEnabled(false);
        token.setModelLimits(null);
        token.setAllowIps(null);
        token.setGroupName("default");
        token.setApplyReason("");

        apiTokenRepository.insert(token);

        log.info("[快速创建Token] 申请Token: userId={}, tokenId={}, status=待审核", userId, token.getId());

        // 返回结果，包含脱敏后的Token
        Map<String, Object> result = new HashMap<>();
        result.put("id", token.getId());
        result.put("name", token.getName());
        result.put("userId", userId);
        result.put("createdTime", token.getCreatedTime());
        result.put("unlimitedQuota", token.getUnlimitedQuota());
        result.put("remainQuota", token.getRemainQuota());
        result.put("expiredTime", token.getExpiredTime());
        result.put("maskedKey", getMaskedKey(key));
        result.put("status", token.getStatus());
        result.put("statusText", "待审核");

        return ApiResponse.success(result);
    }

    /**
     * 管理员：为指定用户创建Token。
     * POST /api/token/admin
     */
    @PostMapping("/admin")
    public ApiResponse<ApiToken> createTokenForUser(@RequestBody AdminCreateTokenRequest request) {
        checkAdminPermission();

        // 验证用户存在
        ApiUser user = apiUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new TokenAuthException(TokenErrorCode.AUTH_FAILED, "用户不存在"));

        if (request.getName() != null && request.getName().length() > 30) {
            return ApiResponse.error(400, "令牌名称过长");
        }

        // 生成Token Key
        String key = generateTokenKey();

        ApiToken token = new ApiToken();
        token.setUserId(user.getId());
        token.setTokenKey(key);
        token.setName(request.getName() != null ? request.getName() : "未命名Token");
        token.setStatus(ApiToken.STATUS_ENABLED);
        token.setCreatedTime(System.currentTimeMillis() / 1000);
        token.setAccessedTime(System.currentTimeMillis() / 1000);
        token.setExpiredTime(request.getExpiredTime() != null ? request.getExpiredTime() : -1);
        token.setRemainQuota(request.getRemainQuota() != null ? request.getRemainQuota() : 0);
        token.setUsedQuota(0);
        token.setUnlimitedQuota(request.getUnlimitedQuota() != null && request.getUnlimitedQuota());
        token.setModelLimitsEnabled(request.getModelLimitsEnabled() != null && request.getModelLimitsEnabled());
        token.setModelLimits(request.getModelLimits());
        token.setAllowIps(request.getAllowIps());
        token.setGroupName(resolveGroupName(request.getGroup()));

        apiTokenRepository.insert(token);

        log.info("[管理员Token管理] 为用户创建Token: userId={}, username={}, tokenId={}",
                user.getId(), user.getUsername(), token.getId());
        return ApiResponse.success(token);
    }

    /**
     * 更新Token。
     * PUT /api/token
     */
    @PutMapping
    public ApiResponse<ApiToken> updateToken(@RequestBody UpdateTokenRequest request) {
        String userId = getCurrentUserId();

        ApiToken token = apiTokenRepository.findById(request.getId())
                .filter(t -> t.getUserId().equals(userId))
                .orElse(null);

        if (token == null) {
            return ApiResponse.error(404, "Token不存在");
        }

        if (request.getName() != null) {
            if (request.getName().length() > 30) {
                return ApiResponse.error(400, "令牌名称过长");
            }
            token.setName(request.getName());
        }

        if (request.getStatus() != null) {
            // 检查状态变更是否合法
            if (request.getStatus() == ApiToken.STATUS_ENABLED) {
                if (token.getStatus() == ApiToken.STATUS_EXPIRED
                        && token.getExpiredTime() != -1
                        && token.getExpiredTime() <= System.currentTimeMillis() / 1000) {
                    return ApiResponse.error(400, "令牌已过期，无法启用");
                }
                if (token.getStatus() == ApiToken.STATUS_EXHAUSTED
                        && !token.hasQuota()) {
                    return ApiResponse.error(400, "令牌可用额度已用尽，无法启用");
                }
            }
            token.setStatus(request.getStatus());
        }

        if (request.getExpiredTime() != null) {
            token.setExpiredTime(request.getExpiredTime());
        }
        if (request.getRemainQuota() != null) {
            token.setRemainQuota(request.getRemainQuota());
        }
        if (request.getUnlimitedQuota() != null) {
            token.setUnlimitedQuota(request.getUnlimitedQuota());
        }
        if (request.getModelLimitsEnabled() != null) {
            token.setModelLimitsEnabled(request.getModelLimitsEnabled());
        }
        if (request.getModelLimits() != null) {
            token.setModelLimits(request.getModelLimits());
        }
        if (request.getAllowIps() != null) {
            token.setAllowIps(request.getAllowIps());
        }
        if (request.getGroup() != null) {
            token.setGroupName(resolveGroupName(request.getGroup()));
        }

        apiTokenRepository.update(token);

        log.info("[Token管理] 更新Token: userId={}, tokenId={}", userId, token.getId());
        return ApiResponse.success(token);
    }

    /**
     * 管理员：更新任意Token。
     * PUT /api/token/admin
     */
    @PutMapping("/admin")
    public ApiResponse<ApiToken> updateTokenByAdmin(@RequestBody UpdateTokenRequest request) {
        checkAdminPermission();

        ApiToken token = apiTokenRepository.findById(request.getId()).orElse(null);
        if (token == null) {
            return ApiResponse.error(404, "Token不存在");
        }

        if (request.getName() != null) {
            if (request.getName().length() > 30) {
                return ApiResponse.error(400, "令牌名称过长");
            }
            token.setName(request.getName());
        }

        if (request.getStatus() != null) {
            token.setStatus(request.getStatus());
        }

        if (request.getExpiredTime() != null) {
            token.setExpiredTime(request.getExpiredTime());
        }
        if (request.getRemainQuota() != null) {
            token.setRemainQuota(request.getRemainQuota());
        }
        if (request.getUnlimitedQuota() != null) {
            token.setUnlimitedQuota(request.getUnlimitedQuota());
        }
        if (request.getModelLimitsEnabled() != null) {
            token.setModelLimitsEnabled(request.getModelLimitsEnabled());
        }
        if (request.getModelLimits() != null) {
            token.setModelLimits(request.getModelLimits());
        }
        if (request.getAllowIps() != null) {
            token.setAllowIps(request.getAllowIps());
        }
        if (request.getGroup() != null) {
            token.setGroupName(resolveGroupName(request.getGroup()));
        }

        apiTokenRepository.update(token);

        String reviewerId = getCurrentUserId();
        log.info("[管理员Token管理] 更新Token: tokenId={}, userId={}, reviewerId={}",
                token.getId(), token.getUserId(), reviewerId);
        return ApiResponse.success(token);
    }

    /**
     * 删除Token。
     * DELETE /api/token/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteToken(@PathVariable Long id) {
        String userId = getCurrentUserId();

        ApiToken token = apiTokenRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .orElse(null);

        if (token == null) {
            return ApiResponse.error(404, "Token不存在");
        }

        apiTokenRepository.delete(id);

        log.info("[Token管理] 删除Token: userId={}, tokenId={}", userId, id);
        return ApiResponse.success();
    }

    /**
     * 管理员：删除指定用户的Token。
     * DELETE /api/token/admin/{id}
     */
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> deleteTokenByAdmin(@PathVariable Long id) {
        checkAdminPermission();

        ApiToken token = apiTokenRepository.findById(id)
                .orElse(null);

        if (token == null) {
            return ApiResponse.error(404, "Token不存在");
        }

        apiTokenRepository.delete(id);

        // 获取用户信息用于日志
        ApiUser user = apiUserRepository.findById(token.getUserId()).orElse(null);
        String username = user != null ? user.getUsername() : "未知用户";

        log.info("[管理员Token管理] 删除Token: tokenId={}, userId={}, username={}",
                id, token.getUserId(), username);
        return ApiResponse.success();
    }

    /**
     * 管理员：批量删除用户的Token。
     * DELETE /api/token/admin/batch
     */
    @DeleteMapping("/admin/batch")
    public ApiResponse<Map<String, Object>> deleteTokensByAdmin(
            @RequestBody DeleteTokensRequest request) {
        checkAdminPermission();

        if (request.getUserId() == null && request.getTokenIds() == null) {
            return ApiResponse.error(400, "必须提供用户ID或Token ID列表");
        }

        int deletedCount = 0;
        Map<String, Object> result = new HashMap<>();

        if (request.getUserId() != null) {
            // 批量删除指定用户的所有Token
            ApiUser user = apiUserRepository.findById(request.getUserId()).orElse(null);
            if (user == null) {
                return ApiResponse.error(404, "用户不存在");
            }

            List<ApiToken> userTokens = apiTokenRepository.findByUserId(user.getId());
            for (ApiToken token : userTokens) {
                apiTokenRepository.delete(token.getId());
                deletedCount++;
            }

            log.info("[管理员Token管理] 批量删除用户Token: userId={}, username={}, deletedCount={}",
                    user.getId(), user.getUsername(), deletedCount);
        }

        if (request.getTokenIds() != null && !request.getTokenIds().isEmpty()) {
            // 批量删除指定Token ID列表
            for (Long tokenId : request.getTokenIds()) {
                apiTokenRepository.delete(tokenId);
                deletedCount++;
            }

            log.info("[管理员Token管理] 批量删除Token: tokenIds={}, deletedCount={}",
                    request.getTokenIds(), deletedCount);
        }

        result.put("deletedCount", deletedCount);
        return ApiResponse.success(result);
    }

    /**
     * 管理员：查询待审批Token列表。
     * GET /api/token/admin/pending
     */
    @GetMapping("/admin/pending")
    public ApiResponse<List<ApiToken>> listPendingTokens(
            @RequestParam(required = false) Integer p,
            @RequestParam(required = false) Integer size) {

        checkAdminPermission();

        int page = p != null && p >= 0 ? p : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, 100) : 10;

        List<ApiToken> tokens = apiTokenRepository.findByStatusPaged(
                ApiToken.STATUS_PENDING, page * pageSize, pageSize);
        return ApiResponse.success(tokens);
    }

    /**
     * 管理员：审批Token申请。
     * PUT /api/token/admin/approve
     */
    @PutMapping("/admin/approve")
    public ApiResponse<ApiToken> approveToken(@RequestBody ApproveTokenRequest request) {
        checkAdminPermission();

        ApiToken token = apiTokenRepository.findById(request.getId())
                .orElse(null);
        if (token == null) {
            return ApiResponse.error(404, "Token不存在");
        }

        // 仅待审核状态的Token可以审批
        if (!token.isPending()) {
            return ApiResponse.error(400, "该Token不在待审核状态");
        }

        String reviewerId = getCurrentUserId();

        if (request.getApproved()) {
            // 审批通过 → status=1 启用
            token.setStatus(ApiToken.STATUS_ENABLED);

            // 管理员可在审批时调整参数
            if (request.getRemainQuota() != null) {
                token.setRemainQuota(request.getRemainQuota());
            }
            if (request.getExpiredTime() != null) {
                token.setExpiredTime(request.getExpiredTime());
            }
            if (request.getUnlimitedQuota() != null) {
                token.setUnlimitedQuota(request.getUnlimitedQuota());
            }
            if (request.getModelLimitsEnabled() != null) {
                token.setModelLimitsEnabled(request.getModelLimitsEnabled());
            }
            if (request.getModelLimits() != null) {
                token.setModelLimits(request.getModelLimits());
            }
            if (request.getAllowIps() != null) {
                token.setAllowIps(request.getAllowIps());
            }

            log.info("[管理员审批] Token审批通过: tokenId={}, reviewerId={}", token.getId(), reviewerId);
        } else {
            // 审批拒绝 → status=5 已拒绝
            token.setStatus(ApiToken.STATUS_REJECTED);
            log.info("[管理员审批] Token审批拒绝: tokenId={}, reviewerId={}", token.getId(), reviewerId);
        }

        token.setReviewedBy(reviewerId);
        token.setReviewedTime(System.currentTimeMillis() / 1000);
        token.setReviewRemark(request.getReviewRemark() != null ? request.getReviewRemark() : "");

        apiTokenRepository.update(token);

        return ApiResponse.success(token);
    }

    /**
     * 获取当前登录用户ID。
     * 优先从会话上下文获取（通过会话认证），其次从Token上下文获取。
     */
    private String getCurrentUserId() {
        // 优先从会话上下文获取
        UserSessionContext sessionContext = UserSessionContext.get();
        if (sessionContext.getUserId() != null) {
            return sessionContext.getUserId();
        }
        throw new TokenAuthException(TokenErrorCode.AUTH_FAILED, "请先登录");
    }

    /**
     * 获取脱敏后的Token。
     */
    private String getMaskedKey(String key) {
        if (key == null || key.length() < 10) {
            return "***";
        }
        return key.substring(0, 6) + "***" + key.substring(key.length() - 4);
    }

    /**
     * 检查管理员权限。
     */
    private void checkAdminPermission() {
        UserSessionContext sessionContext = UserSessionContext.get();
        if (sessionContext.getUserId() == null) {
            throw new TokenAuthException(TokenErrorCode.AUTH_FAILED, "请先登录");
        }
        if (!sessionContext.isAdmin()) {
            throw new TokenAuthException(TokenErrorCode.PERMISSION_DENIED, "需要管理员权限");
        }
    }

    /**
     * 获取当前用户信息。
     */
    private ApiUser getCurrentUser() {
        UserSessionContext sessionContext = UserSessionContext.get();
        String userId = sessionContext.getUserId();
        if (userId == null) {
            throw new TokenAuthException(TokenErrorCode.AUTH_FAILED, "请先登录");
        }

        return apiUserRepository.findById(userId).orElse(null);
    }

    /**
     * 生成Token Key，格式 sk-{随机字符串}。
     */
    private String generateTokenKey() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "sk-" + encoded;
    }

    /**
     * 解析分组名称，null 或空字符串统一为 "default"。
     */
    private String resolveGroupName(String group) {
        return (group != null && !group.isEmpty()) ? group : "default";
    }

    // ==================== 认证开关配置 ====================

    /**
     * 获取聊天接口认证开关状态。
     * GET /api/token/admin/auth-switch
     */
    @GetMapping("/admin/auth-switch")
    public ApiResponse<Map<String, Object>> getAuthSwitch() {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SystemConfig> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(SystemConfig::getConfigKey, SystemConfig.KEY_CHAT_AUTH_REQUIRED);
            SystemConfig config = systemConfigMapper.selectOne(wrapper);

            boolean enabled = config == null || !"0".equals(config.getConfigValue());
            return ApiResponse.success(Map.of(
                    "enabled", enabled,
                    "description", "聊天接口Token认证开关"
            ));
        } catch (Exception e) {
            log.error("获取认证开关失败: {}", e.getMessage());
            return ApiResponse.success(Map.of(
                    "enabled", true,
                    "description", "聊天接口Token认证开关"
            ));
        }
    }

    /**
     * 更新聊天接口认证开关。
     * PUT /api/token/admin/auth-switch
     */
    @PutMapping("/admin/auth-switch")
    public ApiResponse<Void> updateAuthSwitch(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ApiResponse.error("参数错误: enabled 不能为空");
        }

        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SystemConfig> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(SystemConfig::getConfigKey, SystemConfig.KEY_CHAT_AUTH_REQUIRED);
            SystemConfig config = systemConfigMapper.selectOne(wrapper);

            if (config != null) {
                config.setConfigValue(enabled ? "1" : "0");
                systemConfigMapper.updateById(config);
            } else {
                config = new SystemConfig();
                config.setConfigKey(SystemConfig.KEY_CHAT_AUTH_REQUIRED);
                config.setConfigValue(enabled ? "1" : "0");
                config.setDescription("聊天接口是否需要Token认证: 1=需要认证, 0=免认证");
                systemConfigMapper.insert(config);
            }

            log.info("[Token管理] 认证开关已更新: enabled={}", enabled);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("更新认证开关失败: {}", e.getMessage());
            return ApiResponse.error("更新认证开关失败: " + e.getMessage());
        }
    }

    // ==================== 请求DTO ====================

    @lombok.Data
    public static class CreateTokenRequest {
        private String name;
        private Long expiredTime;
        private Integer remainQuota;
        private Boolean unlimitedQuota;
        private Boolean modelLimitsEnabled;
        private String modelLimits;
        private String allowIps;
        private String group;
        /** 申请用途说明 */
        private String applyReason;
    }

    @lombok.Data
    public static class AdminCreateTokenRequest {
        private String userId;
        private String name;
        private Long expiredTime;
        private Integer remainQuota;
        private Boolean unlimitedQuota;
        private Boolean modelLimitsEnabled;
        private String modelLimits;
        private String allowIps;
        private String group;
    }

    @lombok.Data
    public static class UpdateTokenRequest {
        private Long id;
        private String name;
        private Integer status;
        private Long expiredTime;
        private Integer remainQuota;
        private Boolean unlimitedQuota;
        private Boolean modelLimitsEnabled;
        private String modelLimits;
        private String allowIps;
        private String group;
    }

    @lombok.Data
    public static class DeleteTokensRequest {
        private String userId;
        private List<Long> tokenIds;
    }

    @lombok.Data
    public static class ApproveTokenRequest {
        /** Token ID */
        private Long id;
        /** 是否通过 */
        private Boolean approved;
        /** 审批备注 */
        private String reviewRemark;
        /** 剩余配额（通过时可调整） */
        private Integer remainQuota;
        /** 过期时间（通过时可调整） */
        private Long expiredTime;
        /** 是否无限配额（通过时可调整） */
        private Boolean unlimitedQuota;
        /** 是否启用模型限制（通过时可调整） */
        private Boolean modelLimitsEnabled;
        /** 允许的模型列表（通过时可调整） */
        private String modelLimits;
        /** 允许的IP地址（通过时可调整） */
        private String allowIps;
    }
}
