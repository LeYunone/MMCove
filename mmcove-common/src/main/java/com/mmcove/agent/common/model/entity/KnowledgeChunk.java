package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识切片实体,对应 knowledge_chunk 表。
 *
 * <p>Milvus 只存 向量 + chunkId + 标量过滤字段;切片富文本回表本表读取。
 *
 * @since 2026-08-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_chunk")
public class KnowledgeChunk extends BaseEntity<Long> {

    /** 所属文档ID */
    private Long docId;

    /** 切片序号(文档内自增) */
    private Integer seq;

    /** 切片文本 */
    private String content;

    /** Milvus 中对应向量ID */
    private String vectorId;

    /** token 数 */
    private Integer tokenCount;
}
