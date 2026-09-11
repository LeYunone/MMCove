package com.mmcove.agent.common.context;

/**
 * 知识库上下文:承载当前请求选中的知识库 ID(kbId)。
 *
 * <p>由 {@code KnowledgeBaseRouter}(mmcove-rag)在第一次意图识别期写入,
 * {@code KnowledgeMcpTools.searchKnowledge} 在工具线程读取。
 *
 * <p><b>跨线程传播</b>:SSE 流式 + ReAct 工具回调跨虚拟线程 / reactor 调度线程,
 * 普通 {@link ThreadLocal} 不继承。照搬 {@link PlatformUserContext} 的 snapshot/restore 机制,
 * 由 {@code ObservableToolCallback}(mmcove-llm)在工具调用时桥接(见其 knowledgeSnapshot 字段)。
 * 若不桥接,虚拟线程下 kbId 会丢失,searchKnowledge 静默拿到 null。
 *
 * @since 2026-08-03
 */
public class KnowledgeContextHolder {

    /** 知识库 ID 快照:供跨线程(如 SSE 虚拟线程)传播。 */
    public record Snapshot(Long kbId) {
    }

    private static final ThreadLocal<Long> KB_ID = new ThreadLocal<>();

    private KnowledgeContextHolder() {
    }

    /** 设置当前选中的知识库 ID(由 KnowledgeBaseRouter 在意图识别期调用)。 */
    public static void set(Long kbId) {
        KB_ID.set(kbId);
    }

    /** 取当前选中的知识库 ID(未选中返回 null)。 */
    public static Long get() {
        return KB_ID.get();
    }

    /** 拍摄当前知识库 ID 快照(供跨线程传播);无值时返回 null。 */
    public static Snapshot snapshot() {
        Long kbId = KB_ID.get();
        return kbId == null ? null : new Snapshot(kbId);
    }

    /** 在当前线程恢复快照(配合 {@link #snapshot()} 跨线程传播);snapshot 为 null 时清空本线程上下文。 */
    public static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        KB_ID.set(snapshot.kbId());
    }

    public static void clear() {
        KB_ID.remove();
    }
}
