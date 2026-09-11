package com.mmcove.agent.llm.gateway;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 动态渠道持有器，通过 ThreadLocal 在请求线程中传递已解析的渠道信息。
 * 用于在 mmcove-llm 模块的 DefaultLlmGateway 中获取上层模块解析的动态渠道。
 */
public class DynamicChannelHolder {

    private static final ThreadLocal<ResolvedChannel> HOLDER = new ThreadLocal<>();

    /**
     * 已解析的渠道信息。
     */
    public record ResolvedChannel(ChatModel chatModel, String modelName, int channelType) {}

    public static void set(ChatModel chatModel, String modelName, int channelType) {
        HOLDER.set(new ResolvedChannel(chatModel, modelName, channelType));
    }

    public static ResolvedChannel get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
