package com.mmcove.agent.controller;

import com.mmcove.agent.common.context.UserSessionContext;
import com.mmcove.agent.common.enums.TokenErrorCode;
import com.mmcove.agent.common.exception.TokenAuthException;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.SystemConfig;
import com.mmcove.agent.service.SystemConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置管理控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取所有系统配置。
     * GET /api/system-config
     */
    @GetMapping
    public ApiResponse<List<SystemConfig>> getAllConfigs() {
        checkAdminPermission();
        return ApiResponse.success(systemConfigService.getAllConfig());
    }

    /**
     * 批量更新系统配置。
     * PUT /api/system-config
     */
    @PutMapping
    public ApiResponse<Void> updateConfigs(@RequestBody UpdateConfigsRequest request) {
        checkAdminPermission();

        if (request.getConfigs() == null || request.getConfigs().isEmpty()) {
            return ApiResponse.error("配置列表不能为空");
        }

        for (ConfigItem item : request.getConfigs()) {
            if (item.getConfigKey() == null || item.getConfigKey().isEmpty()) {
                continue;
            }
            systemConfigService.updateConfig(item.getConfigKey(), item.getConfigValue(), item.getDescription());
        }

        log.info("[系统配置] 批量更新成功: count={}", request.getConfigs().size());
        return ApiResponse.success(null);
    }

    private void checkAdminPermission() {
        UserSessionContext sessionContext = UserSessionContext.get();
        if (sessionContext.getUserId() == null) {
            throw new TokenAuthException(TokenErrorCode.AUTH_FAILED, "请先登录");
        }
        if (!sessionContext.isAdmin()) {
            throw new TokenAuthException(TokenErrorCode.PERMISSION_DENIED, "需要管理员权限");
        }
    }

    @Data
    public static class UpdateConfigsRequest {
        private List<ConfigItem> configs;
    }

    @Data
    public static class ConfigItem {
        private String configKey;
        private String configValue;
        private String description;
    }
}
