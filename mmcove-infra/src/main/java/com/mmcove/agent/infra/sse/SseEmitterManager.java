package com.mmcove.agent.infra.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接管理器。
 */
@Slf4j
@Component
public class SseEmitterManager {

    private static final long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30 分钟

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> {
            emitters.remove(sessionId);
            log.debug("SSE 连接完成: {}", sessionId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.debug("SSE 连接超时: {}", sessionId);
        });

        emitter.onError(e -> {
            emitters.remove(sessionId);
            log.debug("SSE 连接异常: {}, 会话: {}", e.getMessage(), sessionId);
        });

        return emitter;
    }

    public void sendEvent(String sessionId, SseEvent event) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            log.warn("未找到会话对应的 SSE 连接: {}", sessionId);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getEvent())
                    .data(event.getData())
                    .id(event.getId()));
        } catch (IOException e) {
            log.error("向会话 {} 发送 SSE 事件失败: {}", sessionId, e.getMessage());
            emitters.remove(sessionId);
        }
    }

    public void completeEmitter(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    public void closeAll() {
        emitters.forEach((sessionId, emitter) -> emitter.complete());
        emitters.clear();
    }
}
