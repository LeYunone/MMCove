package com.mmcove.agent.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务计划 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPlanDto {

    /** 计划 ID */
    private String planId;
    /** 是否需要拆分 */
    private boolean needSplit;
    /** 子任务列表 */
    private List<TaskItem> tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        /** 子任务序号（从 0 开始） */
        private int index;
        /** 子任务标题 */
        private String title;
        /** 是否可并行执行 */
        private boolean parallel;
    }
}
