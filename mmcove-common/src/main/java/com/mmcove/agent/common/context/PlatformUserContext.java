package com.mmcove.agent.common.context;

/**
 * 请求用户上下文：承载当前请求的登录用户身份（userId）。
 *
 * <p>由 {@code AuthInterceptor} 在鉴权通过后，从本地 JWT / sk-Token 解析出 userId 填入。
 * 会话/对话等链路通过 {@link #getUserId()} 获取当前用户，用于按用户隔离会话、计数等。
 *
 * <p><b>跨线程传播</b>：SSE 流式接口把执行丢到虚拟线程池（{@code newVirtualThreadPerTaskExecutor}），
 * 普通 {@code ThreadLocal} 不会继承。调用方需在提交异步任务前 {@link #snapshot()}，在任务体首行
 * {@link #restore(Snapshot)} 恢复，任务结束 {@link #clear()}。
 */
public class PlatformUserContext {

    /** 平台身份快照:供跨线程(如 SSE 虚拟线程)传播。 */
    public record Snapshot(String userId, String orgId, String sysTag) {
    }

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ORG_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SYS_TAG = new ThreadLocal<>();

    private PlatformUserContext() {
    }

    /** 设置完整平台身份(userId/orgId/sysTag),用于转发 JWT 解析后的场景。orgId/sysTag 可为 null。 */
    public static void set(String userId, String orgId, String sysTag) {
        USER_ID.set(userId);
        ORG_ID.set(orgId);
        SYS_TAG.set(sysTag);
    }

    /** 仅设置 userId(向后兼容:device-center 代理 X-Platform-User-Id 通道)。 */
    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static String getOrgId() {
        return ORG_ID.get();
    }

    public static String getSysTag() {
        return SYS_TAG.get();
    }

    /** 拍摄当前平台身份快照(供跨线程传播);无 userId 时返回 null。 */
    public static Snapshot snapshot() {
        String uid = USER_ID.get();
        if (uid == null) {
            return null;
        }
        return new Snapshot(uid, ORG_ID.get(), SYS_TAG.get());
    }

    /** 在当前线程恢复快照(配合 {@link #snapshot()} 跨线程传播);snapshot 为 null 时清空本线程上下文。 */
    public static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        USER_ID.set(snapshot.userId());
        ORG_ID.set(snapshot.orgId());
        SYS_TAG.set(snapshot.sysTag());
    }

    public static void clear() {
        USER_ID.remove();
        ORG_ID.remove();
        SYS_TAG.remove();
    }
}
