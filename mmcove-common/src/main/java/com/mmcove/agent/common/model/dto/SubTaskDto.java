package com.mmcove.agent.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子任务 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTaskDto {

    /** 子任务序号 */
    private int taskIndex;
    /** 子任务标题 */
    private String title;
    /** 是否可并行 */
    private boolean parallel;
    /** 子任务状态 */
    private String status;
    /** 子任务结果 */
    private String result;
    /** 错误信息 */
    private String errorMessage;
}
