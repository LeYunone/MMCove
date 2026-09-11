package com.mmcove.agent.mcp.confirm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 危险操作待确认存储(P2-C)。
 *
 * <p>供 @DangerousOperation 标注的高危 @Tool(删除设备/出厂重置)暂存"执行回调 + 一次性令牌"。
 * 工具首次调用 {@link #store} 存入回调并返回令牌,工具随之返回 ui_render 确认契约;
 * 用户在前端点确认按钮 → DangerConfirmController 调 {@link #retrieveAndRemove} 取出回调执行真正 RPC。
 * <b>令牌一次性</b>(取出即移除),TTL 300s 过期,daemon 虚拟线程定期清理。
 *
 * <p>不复用 PendingActionStore:后者耦合 (authToken/systemName/integration 执行器),语义不符;
 * 本类专为 device 危险确认,存 {@code Supplier<String>} 回调,确认执行零路由样板——
 * 每加一个高危操作只改自己的 @Tool,确认接口通用不变。
 *
 * @since 2026-07-16
 */
@Component
public class DangerActionStore {

    private static final Logger log = LoggerFactory.getLogger(DangerActionStore.class);

    /** 默认 TTL:300 秒(与 PendingActionStore 一致)。 */
    private static final int DEFAULT_TTL_SECONDS = 300;

    private final Map<String, PendingDanger> store = new ConcurrentHashMap<>();
    private final int ttlSeconds;

    public DangerActionStore() {
        this(DEFAULT_TTL_SECONDS);
    }

    public DangerActionStore(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        startCleanupTask();
    }

    /**
     * 存储待确认危险操作,返回一次性确认令牌。
     *
     * @param summary    操作摘要(展示给用户)
     * @param actionType 操作类型(如 deleteDevice / factoryReset,前端据此映射按钮文案)
     * @param target     操作目标(如 deviceKey,展示/日志用)
     * @param executor   真正执行回调(确认时调用,返回工具风格 JSON)
     * @return 确认令牌
     */
    public String store(String summary, String actionType, String target, Supplier<String> executor) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        store.put(token, new PendingDanger(token, summary, actionType, target, executor, expiresAt));
        log.info("[DangerActionStore] 存储待确认危险操作: type={}, target={}, token={}", actionType, target, token);
        return token;
    }

    /**
     * 取出并移除待确认操作(一次性)。令牌不存在或已过期返回 null。
     */
    public PendingDanger retrieveAndRemove(String confirmationToken) {
        PendingDanger action = store.remove(confirmationToken);
        if (action == null) {
            log.warn("[DangerActionStore] 确认令牌不存在: token={}", confirmationToken);
            return null;
        }
        if (action.isExpired()) {
            log.warn("[DangerActionStore] 确认令牌已过期: token={}, type={}", confirmationToken, action.actionType());
            return null;
        }
        return action;
    }

    /** 启动 daemon 虚拟线程定期清理过期令牌(防内存泄漏)。 */
    private void startCleanupTask() {
        Thread cleanupThread = Thread.ofVirtual()
                .name("danger-action-cleanup")
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
        cleanupThread.start();
    }

    private void cleanup() {
        int before = store.size();
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("[DangerActionStore] 清理过期待确认操作: removed={}, remaining={}", removed, store.size());
        }
    }

    /** 待确认危险操作(不可变)。executor 为真正执行回调,确认时由 Controller 调用。 */
    public record PendingDanger(String token, String summary, String actionType,
                                String target, Supplier<String> executor, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
