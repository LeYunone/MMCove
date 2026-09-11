package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent ↔ 知识库 关联实体,对应 agent_knowledge_base 表(多对多)。
 *
 * <p>用户经前端配置某 Agent 关联哪些知识库(对标 {@link GroupToolConfig} 的 Agent↔工具组关联)。
 * {@code KnowledgeBaseRouter} 的候选集 = 该 Agent 启用的知识库列表。
 *
 * @since 2026-08-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_knowledge_base")
public class AgentKnowledgeBase extends BaseEntity<Long> {

    /** Agent ID */
    private String agentId;

    /** 知识库ID */
    private Long kbId;

    /** 租户ID */
    private Long tenantId;

    /** 是否启用 */
    private Integer enabled;
}
