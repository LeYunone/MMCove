package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品线实体,对应 product_line 表。
 *
 * <p>知识库产品线体系的顶层维度:各产品线下的知识库默认隔离,
 * {@code knowledge_base.product_line_id} 挂产品线,{@code is_shared=1} 的库可跨线读。
 * {@code code} 参与 Milvus collection 命名({@code mmcove_kb_{code}_{name}})防跨线同名冲突,创建后不可改。
 *
 * @since 2026-09-07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_line")
public class ProductLine extends BaseEntity<Long> {

    /** 产品线编码(全局唯一;用于 collection 命名与外部引用,创建后不可改) */
    private String code;

    /** 产品线名称 */
    private String name;

    /** 产品线描述 */
    private String description;

    /** 状态:1启用 0停用 */
    private Integer status;
}
