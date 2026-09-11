package com.mmcove.agent.infra.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器：连接注册、断开、广播。
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket 会话已注册: {}, 当前总会话数: {}", session.getId(), sessions.size());
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session.getId());
        log.info("WebSocket 会话已移除: {}, 当前总会话数: {}", session.getId(), sessions.size());
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Set<String> getActiveSessionIds() {
        return sessions.keySet();
    }

    public void closeAll() {
        sessions.forEach((id, session) -> {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                log.warn("关闭 WebSocket 会话失败 {}: {}", id, e.getMessage());
            }
        });
        sessions.clear();
    }
}
