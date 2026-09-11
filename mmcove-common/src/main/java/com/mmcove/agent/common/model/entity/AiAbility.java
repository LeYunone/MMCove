package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型能力映射实体，对应 ai_ability 表。
 * 用于按 model 路由到具体渠道，支持负载均衡。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_ability")
public class AiAbility extends BaseEntity<Long> {

    /** 分组 */
    private String groupName;

    /** 模型名称 */
    private String model;

    /** 渠道ID */
    private Long channelId;

    /** 是否启用: 1=启用, 0=禁用 */
    private Integer enabled;

    /** 优先级 */
    private Long priority;

    /** 权重 */
    private Integer weight;
}
