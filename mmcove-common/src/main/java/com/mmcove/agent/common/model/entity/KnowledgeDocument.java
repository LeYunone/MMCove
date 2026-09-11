package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识文档实体,对应 knowledge_document 表。
 * 记录入库文档的元数据与状态,原文切片存 {@link KnowledgeChunk}。
 *
 * @since 2026-08-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_document")
public class KnowledgeDocument extends BaseEntity<Long> {

    /** 所属知识库ID */
    private Long kbId;

    /** 文档标题 */
    private String title;

    /** 来源URL/路径 */
    private String sourceUrl;

    /** MIME 类型(markdown/pdf/docx) */
    private String mime;

    /** 入库状态:0待入库 1已入库 2失败 */
    private Integer status;

    /** 租户ID */
    private Long tenantId;
}
