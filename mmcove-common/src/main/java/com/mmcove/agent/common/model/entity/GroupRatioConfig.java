package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分组倍率配置实体，对应 group_ratio_config 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("group_ratio_config")
public class GroupRatioConfig extends BaseEntity<Long> {

    /** 分组名称 */
    private String groupName;

    /** 分组倍率 */
    private Double ratio;
}
