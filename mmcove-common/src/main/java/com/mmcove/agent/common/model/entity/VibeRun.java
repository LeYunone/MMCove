package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Vibe 任务执行实例实体,对应 vibe_run 表。
 *
 * <p>composeVibePrompt 首调时创建(用户 issue 设想的服务端落点):
 * 外部 AI 每阶段经 reportStage 上报进度/产物;对话上下文被压缩丢失提示词时,
 * 经 getRunContext 按本实例做增量恢复(剩余阶段+产物索引),不从头重跑。
 *
 * @since 2026-09-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vibe_run")
public class VibeRun extends BaseEntity<Long> {

    /** 执行实例短码(如 RUN-A1B2C3;埋入提示词头部供恢复引用) */
    private String runCode;

    /** 产品线(product_line.id) */
    private Long productLineId;

    /** 归属调用方(api_token.id;空runId恢复时优先按此隔离,防跨调用方劫持) */
    private Long ownerTokenId;

    /** 流水线(pipeline_template.id) */
    private Long pipelineId;

    /** 用户任务原文 */
    private String taskText;

    /** 意图分类结果 */
    private String intentType;

    /** 状态:RUNNING/COMPLETED/FAILED/ABORTED */
    private String status;

    /** 当前执行到的阶段编码 */
    private String currentStageCode;

    /** 已完成阶段编码 JSON 数组(权威进度状态源) */
    private String completedStages;

    /** compose 时实际下发的生效阶段编码 JSON 数组(应用产品线覆盖后;校验与收敛基准) */
    private String effectiveStages;

    /** 首次组装的完整提示词快照(getRunContext 恢复底稿) */
    private String composedPrompt;
}
