package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Token 用量统计实体，对应 token_usage_quantity 表。
 * 兼容 Go 版本 model.TokenUsageQuantity。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("token_usage_quantity")
public class TokenUsageQuantity extends BaseEntity<Long> {

    /** 用户ID */
    private String userId;

    /** 使用的 Token Key */
    private String tokenKey;

    /** 创建时间（秒） */
    private Long createdTime;

    /** 消耗配额 */
    private Integer usedQuota;

    /** 设备SN码 */
    private String sn;

    /** 设备MAC地址 */
    private String mac;
}
