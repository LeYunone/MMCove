package com.mmcove.agent.common.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件信封 DTO，所有 SSE 事件统一包装。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseEventEnvelope {

    /** 事件唯一 ID */
    private String eventId;
    /** 事件类型 */
    private String type;
    /** 时间戳（毫秒） */
    private Long timestamp;
    /** 子任务 ID（可选） */
    private String subTaskId;
    /** 事件载荷 */
    private Object payload;
}
