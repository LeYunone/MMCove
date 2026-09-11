package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 使用时长统计实体，对应 usage_duration_statistics 表。
 * 兼容 Go 版本 model.UsageDurationStatistics。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("usage_duration_statistics")
public class UsageDurationStatistics extends BaseEntity<Long> {

    /** 开始时间（毫秒） */
    private Long startTime;

    /** 结束时间（毫秒） */
    private Long endTime;

    /** 角色code */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 设备唯一标识（MAC/SN） */
    private String uniqueKey;
}
