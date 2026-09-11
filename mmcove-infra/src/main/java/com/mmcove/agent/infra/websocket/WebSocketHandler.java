package com.mmcove.agent.infra.websocket;

import com.mmcove.agent.common.util.JacksonUtils;
import com.mmcove.agent.infra.websocket.message.WsMessage;
import com.mmcove.agent.infra.websocket.message.WsMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * WebSocket 消息处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.register(session);
        WsMessage connected = WsMessage.of(WsMessageType.CONNECTED, null, "已连接");
        sendMessage(session, connected);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("收到 WebSocket 消息: {}", payload);

        WsMessage wsMessage = JacksonUtils.fromJson(payload, WsMessage.class);
        if (wsMessage == null) {
            sendMessage(session, WsMessage.of(WsMessageType.ERROR, null, "无效的消息格式"));
            return;
        }

        if (wsMessage.getType() == WsMessageType.HEARTBEAT) {
            sendMessage(session, WsMessage.of(WsMessageType.HEARTBEAT, null, "pong"));
            return;
        }

        // CHAT 类型消息由核心编排器通过事件发布机制处理
        log.info("收到聊天消息，会话: {}", wsMessage.getSessionId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session);
        log.info("WebSocket 连接关闭: {}, 状态: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输异常，会话 {}: {}", session.getId(), exception.getMessage());
        sessionManager.remove(session);
    }

    public void sendMessage(WebSocketSession session, WsMessage message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = JacksonUtils.toJson(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败: {}", e.getMessage(), e);
        }
    }

    public void broadcast(WsMessage message) {
        sessionManager.getActiveSessionIds().forEach(sessionId -> {
            WebSocketSession session = sessionManager.getSession(sessionId);
            sendMessage(session, message);
        });
    }
}
