package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Vibe 阶段产物实体,对应 vibe_run_artifact 表。
 *
 * <p>外部 AI 执行流水线时经 reportStage 上报的阶段产出物(复现步骤/根因结论/
 * 评审清单/测试报告等,文本直存)。任务完成后可整批沉淀回知识库
 * (经 KnowledgeIngestService.ingest)——补全"知识库积累"闭环的原料。
 *
 * @since 2026-09-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vibe_run_artifact")
public class VibeRunArtifact extends BaseEntity<Long> {

    /** 执行实例(vibe_run.id) */
    private Long runId;

    /** 阶段编码(pipeline_stage.stage_code) */
    private String stageCode;

    /** 产物标题 */
    private String title;

    /** 产物内容(文本直存,markdown) */
    private String content;

    /** 一句话摘要(可空) */
    private String summary;
}
