package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置实体，对应 system_config 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends BaseEntity<Long> {

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;

    /** 聊天接口认证开关 key */
    public static final String KEY_CHAT_AUTH_REQUIRED = "chat_completions_auth_required";
}
