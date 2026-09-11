package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流水线阶段实体,对应 pipeline_stage 表。
 *
 * <p>全局阶段库:阶段跨流水线共享(多条流水线引用同一阶段 = "融汇贯通"),
 * 修改一处全线生效。{@code rolePrompt} 为带占位符的模板,组装时由
 * VibePromptComposerService 渲染({{task}}/{{knowledge}} 等)。
 *
 * @since 2026-09-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pipeline_stage")
public class PipelineStage extends BaseEntity<Long> {

    /** 阶段编码(全局唯一,流水线以此引用;如 root-cause) */
    private String stageCode;

    /** 阶段名称(如 根因分析) */
    private String name;

    /** 阶段说明(管理侧查看用) */
    private String description;

    /** 角色提示词模板,占位符: {{task}}/{{knowledge}}/{{product_line_name}}/{{product_line_desc}}/{{pipeline_name}}/{{prev_outputs}} */
    private String rolePrompt;

    /** 子agent数(>1 表示并行多视角产出后互审) */
    private Integer subAgentCount;

    /** 产出物要求(一句话,组装进阶段地图) */
    private String deliverable;

    /** 门禁定义:进入下一阶段必须满足的条件(自然语言,由外部AI自检) */
    private String gateRule;

    /** 状态:1启用 0停用 */
    private Integer status;
}
