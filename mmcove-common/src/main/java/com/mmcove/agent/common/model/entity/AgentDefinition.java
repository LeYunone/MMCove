package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 定义实体，存储在数据库 agent_definition 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_definition")
public class AgentDefinition extends BaseEntity<Long> {

    /** Agent 唯一标识，如 general-assistant */
    private String agentId;

    /** Agent 显示名称 */
    private String name;

    /** Agent 描述，用于意图识别 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /** 绑定的工具组名(对应 group_tool_config.group_name);路由命中该 Agent 后,只装载此组启用的工具。
     *  <p>为空时回退到 "default" 组(向后兼容)。这是「意图路由 + 工具收敛」的关键字段:
     *  把路由选 Agent 的结果,与工具装载焊起来——不再全量挂工具,而是只挂该意图相关的 3~5 个。 */
    private String toolGroupName;

    /** 温度参数 */
    private Double temperature;

    /** 最大 token 数 */
    private Integer maxTokens;

    /** 是否默认 Agent */
    private Boolean isDefault;

    /** 状态：ACTIVE / DISABLED */
    private String status;
}
