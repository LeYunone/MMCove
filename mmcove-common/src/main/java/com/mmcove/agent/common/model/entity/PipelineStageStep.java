package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流水线阶段序列实体,对应 pipeline_stage_step 表。
 *
 * <p>流水线对全局阶段库的引用序列:按 {@code seq} 升序执行
 * (留间隔 10/20/30 便于中间插阶段)。{@code stepParams} 为步骤级
 * JSON 覆盖({"subAgentCount":2,"extraPrompt":"..."}),NULL=用阶段默认。
 *
 * @since 2026-09-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pipeline_stage_step")
public class PipelineStageStep extends BaseEntity<Long> {

    /** 所属流水线(pipeline_template.id) */
    private Long pipelineId;

    /** 引用阶段(pipeline_stage.id) */
    private Long stageId;

    /** 执行顺序(与 pipeline_id 联合唯一) */
    private Integer seq;

    /** 步骤级覆盖 JSON({"subAgentCount":2,"extraPrompt":"..."});NULL=用阶段默认 */
    private String stepParams;
}
