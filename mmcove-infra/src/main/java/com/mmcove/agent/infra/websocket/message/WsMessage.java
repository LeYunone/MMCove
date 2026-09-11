package com.mmcove.agent.infra.websocket.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 消息协议体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsMessage {

    private WsMessageType type;
    private String sessionId;
    private String content;
    private Long timestamp;

    public static WsMessage of(WsMessageType type, String sessionId, String content) {
        return WsMessage.builder()
                .type(type)
                .sessionId(sessionId)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
