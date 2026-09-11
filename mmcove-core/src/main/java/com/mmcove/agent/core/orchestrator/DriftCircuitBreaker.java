package com.mmcove.agent.core.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飘移率只读熔断器（失败兜底，评审 ISSUE-006）。
 * <p>滑动窗口统计每会话的工具调用成败，失败率超阈值时进入「只读模式」
 * （AgentOrchestrator 过滤写工具，只留查询类），防止 AI 持续飘移导致误操作。
 * <p>信号源：ParamSpec 校验失败（L5 拦截非法参数 = AI 参数飘移的直接证据）。
 * <p>恢复：窗口滑动后失败率回落 → 自动解除（无需人工干预）。
 */
@Slf4j
@Component
public class DriftCircuitBreaker {

    /** 滑动窗口大小（最近 N 次工具调用） */
    private static final int WINDOW_SIZE = 10;
    /** 失败率阈值（超此进入只读模式） */
    private static final double FAILURE_THRESHOLD = 0.5;
    /** 最小样本数（不足此数不判断，避免小样本误判） */
    private static final int MIN_SAMPLES = 4;

    private final ConcurrentHashMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    /**
     * 记录一次失败（ParamSpec 校验失败 / 工具调用异常）。
     */
    public void recordFailure(String sessionId) {
        if (sessionId == null) {
            return;
        }
        windows.computeIfAbsent(sessionId, k -> new SlidingWindow()).record(false);
        if (isOpen(sessionId)) {
            log.warn("[DriftBreaker] 会话 {} 飘移熔断触发（失败率超 {}%），进入只读模式",
                    sessionId, (int) (FAILURE_THRESHOLD * 100));
        }
    }

    /**
     * 记录一次成功。
     */
    public void recordSuccess(String sessionId) {
        if (sessionId == null) {
            return;
        }
        windows.computeIfAbsent(sessionId, k -> new SlidingWindow()).record(true);
    }

    /**
     * 是否处于只读模式（熔断开）。
     */
    public boolean isOpen(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        SlidingWindow window = windows.get(sessionId);
        if (window == null) {
            return false;
        }
        return window.failureRate() >= FAILURE_THRESHOLD && window.size() >= MIN_SAMPLES;
    }

    /**
     * 滑动窗口（最近 WINDOW_SIZE 次成败，Deque 实现，synchronized 保证线程安全）。
     */
    private static class SlidingWindow {
        private final Deque<Boolean> records = new ArrayDeque<>();

        synchronized void record(boolean success) {
            records.addLast(success);
            while (records.size() > WINDOW_SIZE) {
                records.removeFirst();
            }
        }

        synchronized double failureRate() {
            if (records.isEmpty()) {
                return 0.0;
            }
            long failures = records.stream().filter(b -> !b).count();
            return (double) failures / records.size();
        }

        synchronized int size() {
            return records.size();
        }
    }
}
