package com.mmcove.agent.infra.websocket.message;

/**
 * WebSocket 消息类型枚举。
 */
public enum WsMessageType {

    CHAT,
    CHAT_RESPONSE,
    TOOL_CALL,
    TOOL_RESULT,
    ERROR,
    HEARTBEAT,
    CONNECTED,
    DISCONNECTED
}
