package com.mmcove.agent.infra.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseEvent {

    private String event;
    private String data;
    private String id;

    public static SseEvent of(String event, String data) {
        return SseEvent.builder()
                .event(event)
                .data(data)
                .id(String.valueOf(System.currentTimeMillis()))
                .build();
    }
}
