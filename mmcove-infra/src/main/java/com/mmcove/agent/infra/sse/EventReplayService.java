package com.mmcove.agent.infra.sse;

import com.mmcove.agent.common.model.entity.SseEventRecord;
import com.mmcove.agent.infra.persistence.repository.SseEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 事件重放服务：从数据库加载历史事件并重放到新的 SSE 连接。
 * <p>
 * 生产限制：最多重放 500 条事件，防止长会话一次性加载过多数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReplayService {

    private static final int MAX_REPLAY_EVENTS = 500;

    private final SseEventRepository sseEventRepository;

    /**
     * 重放指定会话中 lastEventId 之后的所有事件。
     *
     * @param emitter     目标 SSE 发射器
     * @param sessionId   会话 ID
     * @param lastEventId 上次消费的最后事件 ID
     */
    public void replay(SseEmitter emitter, String sessionId, String lastEventId) {
        List<SseEventRecord> events;
        if (lastEventId == null || lastEventId.isBlank()) {
            events = sseEventRepository.findBySessionId(sessionId);
        } else {
            events = sseEventRepository.findAfterEventId(sessionId, lastEventId);
        }

        // 超过限制时只取最近的事件
        if (events.size() > MAX_REPLAY_EVENTS) {
            log.warn("重放事件数超限: sessionId={}, total={}, capped={}",
                    sessionId, events.size(), MAX_REPLAY_EVENTS);
            events = events.subList(events.size() - MAX_REPLAY_EVENTS, events.size());
        }

        log.info("重放 SSE 事件: sessionId={}, lastEventId={}, count={}", sessionId, lastEventId, events.size());

        for (SseEventRecord record : events) {
            try {
                emitter.send(SseEmitter.event()
                        .name(record.getEventType())
                        .data(record.getData())
                        .id(record.getEventId()));
            } catch (IOException e) {
                log.error("重放事件失败: eventId={}", record.getEventId());
                break;
            }
        }
    }
}
