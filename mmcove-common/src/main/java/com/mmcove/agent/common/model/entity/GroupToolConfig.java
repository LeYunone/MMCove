package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分组工具配置实体，对应 group_tool_config 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("group_tool_config")
public class GroupToolConfig extends BaseEntity<Long> {

    /** 分组名称 */
    private String groupName;

    /** 工具名称 */
    private String toolName;

    /** 是否启用 */
    private Integer enabled;
}
