package com.mmcove.agent.llm.callback;

/**
 * 实体记忆消费回调(解耦 mmcove-llm → mmcove-core 依赖,同 {@link ObservableToolCallback.ThinkingTurnConsumer} 模式)。
 *
 * <p>{@link ObservableToolCallback} 在工具调用后调本接口,
 * 由 AgentOrchestrator(mmcove-core)注入实现:解析 resolveDeviceOrGroup 返回结果 → 调 EntityMemoryService 记忆。
 * 这样 ObservableToolCallback(mmcove-llm)无需依赖 EntityMemoryService(mmcove-core),避免循环依赖。
 *
 * @since 2026-07-21
 */
@FunctionalInterface
public interface EntityMemoryConsumer {

    /**
     * @param sessionId  会话 ID
     * @param toolName   工具名(如 resolveDeviceOrGroup)
     * @param toolInput  工具入参 JSON(含用户原话 target)
     * @param toolResult 工具完整返回 JSON(含 deviceKey/groupId)
     */
    void accept(String sessionId, String toolName, String toolInput, String toolResult);
}
