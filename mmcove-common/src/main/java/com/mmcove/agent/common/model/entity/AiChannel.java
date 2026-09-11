package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI渠道配置实体，对应 ai_channel 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_channel")
public class AiChannel extends BaseEntity<Long> {

    /** 渠道名称 */
    private String name;

    /** 模型列表（逗号分隔） */
    private String models;

    /** 状态（1=启用） */
    private Integer status;

    /** 渠道类型: 1=OpenAI, 2=Anthropic, 14=智谱AI(Anthropic兼容) */
    private Integer type;

    /** API密钥 */
    private String apiKey;

    /** API基础URL */
    private String baseUrl;

    /** 权重(负载均衡) */
    private Integer weight;

    /** 优先级(越大越高) */
    private Long priority;

    /** 是否自动禁用: 0=否, 1=是 */
    private Integer autoBan;

    /** 模型名映射JSON, 如 {"gpt-4o":"gpt-4o-2024-05-13"} */
    private String modelMapping;

    /** 最近响应时间(ms) */
    private Integer responseTime;

    /** 已使用Token数 */
    private Long usedQuota;

    /** 是否系统默认渠道(yml配置) */
    private Integer isSystem;

    /** 所属分组 */
    private String groupName;

    /** 渠道角色（逗号分隔） */
    private String role;

    // ==================== 渠道类型常量 ====================

    public static final int TYPE_OPENAI = 1;
    public static final int TYPE_ANTHROPIC = 2;
    /** 智谱AI（BigModel），走 Anthropic 兼容协议 */
    public static final int TYPE_ZHIPU = 14;

    // ==================== 业务方法 ====================

    public boolean isEnabled() {
        return status != null && status == 1;
    }

    public boolean isSystemChannel() {
        return isSystem != null && isSystem == 1;
    }
}
