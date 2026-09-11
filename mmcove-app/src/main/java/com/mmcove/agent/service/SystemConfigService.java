package com.mmcove.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.SystemConfig;
import com.mmcove.agent.infra.persistence.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统配置服务，封装 system_config 表的读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    /**
     * 查询单个配置值，不存在则返回默认值。
     */
    public String getConfig(String key, String defaultValue) {
        try {
            SystemConfig config = findByKey(key);
            return config != null ? config.getConfigValue() : defaultValue;
        } catch (Exception e) {
            log.debug("[系统配置] 读取失败: key={}, 使用默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 查询布尔配置值。
     */
    public boolean getBooleanConfig(String key, boolean defaultValue) {
        String value = getConfig(key, null);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 查询长整型配置值。
     */
    public long getLongConfig(String key, long defaultValue) {
        String value = getConfig(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 查询所有系统配置。
     */
    public List<SystemConfig> getAllConfig() {
        return systemConfigMapper.selectList(null);
    }

    /**
     * 更新或插入配置项。
     */
    public void updateConfig(String key, String value, String description) {
        SystemConfig config = findByKey(key);
        if (config != null) {
            config.setConfigValue(value);
            if (description != null && !description.isEmpty()) {
                config.setDescription(description);
            }
            systemConfigMapper.updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description != null ? description : "");
            systemConfigMapper.insert(config);
        }
        log.info("[系统配置] 已更新: key={}, value={}", key, value);
    }

    /**
     * 按 key 查询配置记录。
     */
    private SystemConfig findByKey(String key) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, key);
        return systemConfigMapper.selectOne(wrapper);
    }
}
