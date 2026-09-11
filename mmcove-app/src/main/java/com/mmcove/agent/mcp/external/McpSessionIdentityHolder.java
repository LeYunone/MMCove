package com.mmcove.agent.mcp.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 会话身份桥(跨线程传递 token 绑定的产品线)。
 *
 * <p>背景:MCP SDK 在会话内部线程(非 POST servlet 线程)执行工具,
 * TokenAuthContext(ThreadLocal)在工具线程读不到 —— 需显式桥接:
 * <ol>
 *   <li>捕获:AuthInterceptor 处理 POST /mcp/message?sessionId=xxx 时(servlet 线程,
 *       token 已校验)记录 sessionId → (productLineId, ownerTokenId);</li>
 *   <li>消费:McpServerConfig 的工具规格包装层用 {@code exchange.sessionId()}
 *       查本表,把身份写入工具线程的 TokenAuthContext(用完清空)。</li>
 * </ol>
 *
 * <p>MCP SDK 2.0 的 sessionId() 与 POST query 的 sessionId 同源,无需反射。
 * 会话条目带时间戳,捕获时顺带清扫 12 小时无活动的废弃会话(断连重连会生成新 sessionId)。
 *
 * @since 2026-09-11
 */
@Slf4j
@Component
public class McpSessionIdentityHolder {

    /** 会话身份条目的闲置过期时间(毫秒) */
    private static final long IDLE_EXPIRE_MS = 12L * 60 * 60 * 1000;

    /** endpoint sessionId → [productLineId, ownerTokenId, lastActiveMillis] */
    private final Map<String, Object[]> sessionLines = new ConcurrentHashMap<>();

    /** 捕获会话身份(拦截器在 POST /mcp/message 时调用;productLineId/ownerTokenId 可为 null=未绑定) */
    public void capture(String sessionId, Long productLineId, Long ownerTokenId) {
        long now = System.currentTimeMillis();
        // 顺带清扫过期会话(低频写时清扫,避免引入定时器)
        sessionLines.entrySet().removeIf(e -> now - (Long) e.getValue()[2] > IDLE_EXPIRE_MS);
        sessionLines.put(sessionId, new Object[]{productLineId, ownerTokenId, now});
        log.debug("[MCP身份桥] 捕获会话身份: sessionId={}, productLineId={}, ownerTokenId={}",
                sessionId, productLineId, ownerTokenId);
    }

    /** 从工具调用的会话 id 解析该会话绑定的产品线(可 null=未绑定,降级只读共享库) */
    public Long resolveLine(String sessionId) {
        Object[] entry = entryOf(sessionId);
        return entry == null ? null : (Long) entry[0];
    }

    /** 解析该会话归属的调用方 token id(可 null;vibe run 归属隔离用) */
    public Long resolveOwnerId(String sessionId) {
        Object[] entry = entryOf(sessionId);
        return entry == null ? null : (Long) entry[1];
    }

    private Object[] entryOf(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Object[] entry = sessionLines.get(sessionId);
        if (entry == null) {
            log.warn("[MCP身份桥] 会话未注册身份(可能是服务重启前的旧连接),按未绑定产品线处理: sessionId={}", sessionId);
            return null;
        }
        entry[2] = System.currentTimeMillis();
        return entry;
    }
}
