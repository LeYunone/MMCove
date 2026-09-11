package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SSE 事件记录实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sse_event_record")
public class SseEventRecord extends BaseEntity<Long> {

    private String sessionId;
    private String eventId;
    private String eventType;
    private String subTaskId;
    /** JSON 格式的完整事件数据 */
    private String data;
}
