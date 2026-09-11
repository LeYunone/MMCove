package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具定义实体，用于动态管理 @Tool 方法的描述和参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tool_definition")
public class ToolDefinition extends BaseEntity<Long> {

    /** 工具方法名，对应 @Tool 注解的方法名 */
    private String toolName;

    /** 工具描述（可自定义覆盖原始描述） */
    private String description;

    /** 工具输入参数 Schema（JSON 格式） */
    private String inputSchema;

    /** 是否为危险操作 */
    private Boolean dangerous;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /** 来源类名 */
    private String sourceClass;

    /** 原始描述（用于重置对比） */
    private String originalDescription;
}
