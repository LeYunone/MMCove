package com.mmcove.agent.infra.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.enums.SseEventType;
import com.mmcove.agent.common.model.dto.SseEventEnvelope;
import com.mmcove.agent.common.model.entity.SseEventRecord;
import com.mmcove.agent.infra.persistence.repository.SseEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * SSE 事件存储服务：负责持久化事件并发送到 SSE 客户端。
 * <p>
 * 生产优化：
 * - 异步批量持久化：事件先入内存队列，定时刷盘（500ms 或满 20 条），不阻塞主流程
 * - JSON 单次序列化：envelope 只序列化一次，持久化和发送共用
 * - SseEmitter 同步发送：send() 内部已加锁，线程安全
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseEventStore {

    private final SseEventRepository sseEventRepository;
    private final ObjectMapper objectMapper;

    /** 批量刷盘阈值 */
    private static final int BATCH_SIZE = 20;
    /** 刷盘间隔（毫秒） */
    private static final long FLUSH_INTERVAL_MS = 500;

    private final BlockingQueue<SseEventRecord> pendingQueue = new ArrayBlockingQueue<>(1024);
    private ScheduledExecutorService flushScheduler;

    @PostConstruct
    public void init() {
        flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-event-flush");
            t.setDaemon(true);
            return t;
        });
        flushScheduler.scheduleWithFixedDelay(this::flushBatch, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        // 关闭前刷盘
        flushBatch();
        if (flushScheduler != null) {
            flushScheduler.shutdown();
        }
    }

    /**
     * 发出 SSE 事件：发送到客户端 + 异步持久化。
     */
    public void emitEvent(SseEmitter emitter, String sessionId,
                          SseEventType type, String subTaskId, Object payload) {
        // emitter==null(同步路径无前端)时跳过 SSE,避免 NPE;
        // 失败兜底(cid/慢调用/思考链/实体记忆/熔断信号)经 ObservableToolCallback 仍生效
        if (emitter == null) {
            return;
        }
        String eventId = generateEventId();

        SseEventEnvelope envelope = SseEventEnvelope.builder()
                .eventId(eventId)
                .type(type.getValue())
                .timestamp(System.currentTimeMillis())
                .subTaskId(subTaskId)
                .payload(payload)
                .build();

        // 单次序列化
        String envelopeJson;
        try {
            envelopeJson = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("SSE 事件序列化失败: type={}", type.getValue(), e);
            return;
        }

        // 1. 异步持久化（入队列）
        SseEventRecord record = new SseEventRecord();
        record.setSessionId(sessionId);
        record.setEventId(eventId);
        record.setEventType(type.getValue());
        record.setSubTaskId(subTaskId);
        record.setData(envelopeJson);
        if (!pendingQueue.offer(record)) {
            log.warn("SSE 事件队列已满，丢弃事件: sessionId={}, type={}", sessionId, type.getValue());
        }

        // 2. 同步发送到客户端（send() 内部已同步，线程安全）
        try {
            emitter.send(SseEmitter.event()
                    .name(type.getValue())
                    .data(envelopeJson)
                    .id(eventId));
        } catch (IOException e) {
            log.error("SSE 事件发送失败: sessionId={}, type={}", sessionId, type.getValue());
        }
    }

    /**
     * 发出简单文本事件（兼容原有 message/done/error 事件）。
     */
    public void emitSimpleEvent(SseEmitter emitter, String sessionId,
                                SseEventType type, String text) {
        String eventId = generateEventId();

        // 持久化
        try {
            SseEventEnvelope envelope = SseEventEnvelope.builder()
                    .eventId(eventId)
                    .type(type.getValue())
                    .timestamp(System.currentTimeMillis())
                    .payload(text)
                    .build();
            String jsonData = objectMapper.writeValueAsString(envelope);
            SseEventRecord record = new SseEventRecord();
            record.setSessionId(sessionId);
            record.setEventId(eventId);
            record.setEventType(type.getValue());
            record.setData(jsonData);
            if (!pendingQueue.offer(record)) {
                log.warn("SSE 事件队列已满，丢弃事件: sessionId={}, type={}", sessionId, type.getValue());
            }
        } catch (Exception e) {
            log.warn("SSE 事件持久化失败: {}", e.getMessage());
        }

        // 发送
        try {
            emitter.send(SseEmitter.event()
                    .name(type.getValue())
                    .data(text)
                    .id(eventId));
        } catch (IOException e) {
            log.error("SSE 事件发送失败: sessionId={}, type={}", sessionId, type.getValue());
        }
    }

    /**
     * 定时批量刷盘：从队列中取出事件批量写入 DB。
     */
    private void flushBatch() {
        List<SseEventRecord> batch = new ArrayList<>(BATCH_SIZE);
        pendingQueue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            for (SseEventRecord record : batch) {
                sseEventRepository.save(record);
            }
        } catch (Exception e) {
            log.error("SSE 事件批量持久化失败: count={}", batch.size(), e);
        }
    }

    /**
     * 生成事件 ID：雪花式时间戳 + 随机后缀，避免碰撞。
     */
    private String generateEventId() {
        return "evt_" + Long.toHexString(System.currentTimeMillis())
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
