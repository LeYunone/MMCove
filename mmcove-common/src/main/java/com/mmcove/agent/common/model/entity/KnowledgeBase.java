package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库实体,对应 knowledge_base 表。
 *
 * <p>一个知识库 = 一组文档的集合,绑定一个 Embedding 渠道和一个 Milvus collection。
 * {@code description}/{@code keywords} 作为 {@code KnowledgeBaseRouter} 选库的依据
 * (对标 {@link AgentDefinition#getDescription()})。
 *
 * @since 2026-08-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity<Long> {

    /** 知识库名称(租户内唯一) */
    private String name;

    /** 知识库描述(LLM/语义选库的核心依据) */
    private String description;

    /** 关键词,逗号分隔(选库规则匹配用) */
    private String keywords;

    /** 场景标签 */
    private String sceneTags;

    /** 绑定的 Embedding 渠道名(对应 mmcove.rag.embedding.channels[].name) */
    private String embeddingChannel;

    /** Milvus collection 名 */
    private String collectionName;

    /** 向量维度(必须与 Embedding 渠道一致) */
    private Integer dimensions;

    /** 同等相关度时的优先级 tiebreaker */
    private Integer priority;

    /** 所属产品线ID(产品线间默认隔离) */
    private Long productLineId;

    /** 是否共享库:0私有(仅归属产品线可读) 1共享(所有产品线可读,仅归属线可写) */
    private Boolean isShared;

    /** 租户ID */
    private Long tenantId;

    /** 状态:1启用 0停用 */
    private Integer status;
}
