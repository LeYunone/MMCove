package com.mmcove.agent.tools.external.confirmation;

import com.mmcove.agent.tools.external.model.PendingAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待确认操作存储
 * 内存ConcurrentHashMap + TTL过期清理
 */
public class PendingActionStore {

    private static final Logger log = LoggerFactory.getLogger(PendingActionStore.class);

    /** 默认TTL：300秒 */
    private static final int DEFAULT_TTL_SECONDS = 300;

    private final Map<String, PendingAction> store = new ConcurrentHashMap<>();
    private final int ttlSeconds;

    public PendingActionStore() {
        this(DEFAULT_TTL_SECONDS);
    }

    public PendingActionStore(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        startCleanupTask();
    }

    /**
     * 存储待确认操作，返回确认令牌
     */
    public PendingAction store(String systemName, String operationName,
                               Object params, String summary, String authToken) {
        String token = UUID.randomUUID().toString().replace("-", "");
        PendingAction action = new PendingAction(token, systemName, operationName, params, summary, authToken, ttlSeconds);
        store.put(token, action);
        log.info("存储待确认操作: system={}, operation={}, token={}", systemName, operationName, token);
        return action;
    }

    /**
     * 获取并验证待确认操作
     * 成功获取后自动移除（一次性令牌）
     */
    public PendingAction retrieveAndRemove(String confirmationToken) {
        PendingAction action = store.remove(confirmationToken);
        if (action == null) {
            log.warn("确认令牌不存在: token={}", confirmationToken);
            return null;
        }
        if (action.isExpired()) {
            log.warn("确认令牌已过期: token={}, operation={}", confirmationToken, action.getOperationName());
            return null;
        }
        return action;
    }

    /**
     * 启动过期清理任务
     */
    private void startCleanupTask() {
        Thread cleanupThread = Thread.ofVirtual()
                .name("pending-action-cleanup")
                .unstarted(() -> {
                    while (true) {
                        try {
                            Thread.sleep(60_000);
                            cleanup();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * 清理过期操作
     */
    private void cleanup() {
        int before = store.size();
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("清理过期待确认操作: removed={}, remaining={}", removed, store.size());
        }
    }
}
