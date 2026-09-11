package com.mmcove.agent.llm.gateway;

import com.mmcove.agent.common.model.dto.ChatResponse;
import com.mmcove.agent.llm.memory.ConversationContext;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLM 网关接口。
 */
public interface LlmGateway {

    /**
     * 同步聊天调用。
     */
    ChatResponse chat(String userMessage, ConversationContext context);

    /**
     * 同步聊天调用（使用指定的工具回调列表）。
     * 用于子任务执行时传入 ObservableToolCallback 包装后的工具回调。
     */
    ChatResponse chatWithTools(String userMessage, ConversationContext context,
                               List<ToolCallback> toolCallbacks);

    /**
     * 流式聊天调用。
     */
    Flux<ChatResponse> chatStream(String userMessage, ConversationContext context);

    /**
     * 流式聊天调用（使用指定的工具回调列表）。
     * 用于 ObservableToolCallback 包装后的工具回调注入。
     */
    Flux<ChatResponse> chatStreamWithTools(String userMessage, ConversationContext context,
                                           List<ToolCallback> toolCallbacks);

    /**
     * 意图识别调用，返回纯文本结果。
     */
    String detectIntent(String prompt);

    /**
     * 轻量级同步调用（不带工具回调）。
     * 适用于任务规划等内部调用场景，避免工具定义干扰 LLM 响应。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return 纯文本响应内容
     */
    String callWithoutTools(String systemPrompt, String userMessage);
}
