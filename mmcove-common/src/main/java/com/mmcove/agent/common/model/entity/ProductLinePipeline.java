package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品线↔流水线 绑定实体,对应 product_line_pipeline 表。
 *
 * <p>产品线维度的流水线定制:{@code stageOverrides} 按 stage_code 键覆盖阶段参数
 * ({"peer-review":{"enabled":false,"subAgentCount":3}}),{@code extraContext}
 * 追加产品线背景(技术栈/目录约定等)。未绑定的产品线用全局默认流水线。
 *
 * @since 2026-09-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_line_pipeline")
public class ProductLinePipeline extends BaseEntity<Long> {

    /** 产品线(product_line.id) */
    private Long productLineId;

    /** 流水线(pipeline_template.id) */
    private Long pipelineId;

    /** 阶段覆盖 JSON(按 stage_code 键:{"peer-review":{"enabled":false,"subAgentCount":3}}) */
    private String stageOverrides;

    /** 产品线补充背景段(追加到主提示词产品线背景段) */
    private String extraContext;

    /** 1该流水线对本线生效 0忽略绑定 */
    private Integer enabled;
}
