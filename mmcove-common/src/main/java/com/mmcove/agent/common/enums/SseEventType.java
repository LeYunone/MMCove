package com.mmcove.agent.common.enums;

/**
 * SSE 事件类型枚举。
 */
public enum SseEventType {

    /** 思考/规划阶段 */
    THINKING("thinking"),
    /** 任务拆分计划 */
    TASK_PLAN("task_plan"),
    /** 子任务开始 */
    TASK_START("task_start"),
    /** 子任务进度（thought/action/observation） */
    TASK_PROGRESS("task_progress"),
    /** 子任务完成 */
    TASK_COMPLETE("task_complete"),
    /** 最终答案开始 */
    FINAL_ANSWER("final_answer"),
    /** 增量文本（兼容） */
    MESSAGE("message"),
    /** 流结束 */
    DONE("done"),
    /** 需要用户确认操作 */
    CONFIRMATION_REQUIRED("confirmation_required"),
    /** 图片生成结果(生图 tool 成功后推送,前端据此渲染图片) */
    IMAGE("image"),
    /** 错误 */
    ERROR("error"),
    /** UI 组件渲染指令(AI 通过 renderUi 工具指令前端渲染业务组件) */
    UI_RENDER("ui_render");

    private final String value;

    SseEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
