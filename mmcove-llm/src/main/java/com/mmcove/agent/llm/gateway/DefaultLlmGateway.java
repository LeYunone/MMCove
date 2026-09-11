package com.mmcove.agent.llm.gateway;

import com.mmcove.agent.common.enums.ChatResponseType;
import com.mmcove.agent.common.exception.LlmCallException;
import com.mmcove.agent.common.model.dto.ChatResponse;
import com.mmcove.agent.llm.memory.ConversationContext;
import com.mmcove.agent.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * 默认 LLM 网关实现，封装 Spring AI ChatClient。
 * 将内置工具（@Tool）传入 ChatClient，由 Spring AI 自动处理 ReAct 循环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLlmGateway implements LlmGateway {

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    /** 渠道类型常量，与 AiChannel 保持一致 */
    private static final int CHANNEL_TYPE_OPENAI = 1;
    private static final int CHANNEL_TYPE_ANTHROPIC = 2;

    /**
     * 解析当前请求应使用的 ChatClient。
     * 优先从 DynamicChannelHolder 获取动态渠道，否则使用默认单例。
     */
    private ChatClient resolveChatClient() {
        DynamicChannelHolder.ResolvedChannel dynamic = DynamicChannelHolder.get();
        if (dynamic == null) {
            return chatClient;
        }
        log.debug("[LlmGateway] 使用动态渠道: model={}, type={}", dynamic.modelName(), dynamic.channelType());
        return buildDynamicChatClient(dynamic);
    }

    /**
     * 根据已解析的渠道信息构建临时 ChatClient。
     * 通过 ChatOptions 覆盖模型名，使请求路由到正确的模型。
     */
    private ChatClient buildDynamicChatClient(DynamicChannelHolder.ResolvedChannel resolved) {
        ChatClient.Builder builder = ChatClient.builder(resolved.chatModel());
        if (resolved.modelName() != null) {
            // Spring AI 2.0: defaultOptions 接收 ChatOptions.Builder；仅覆盖模型名即可路由到正确模型
            builder.defaultOptions(org.springframework.ai.chat.prompt.ChatOptions.builder().model(resolved.modelName()));
        }
        return builder.build();
    }

    @Override
    public ChatResponse chat(String userMessage, ConversationContext context) {
        try {
            List<ToolCallback> toolCallbacks = toolRegistry.getToolCallbacks();

            ChatClient activeClient = resolveChatClient();
            ChatClient.ChatClientRequestSpec request = activeClient.prompt();

            if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
                request = request.system(context.getSystemPrompt());
            }

            if (context.getMessages() != null && !context.getMessages().isEmpty()) {
                request = request.messages(context.getMessages());
            }

            if (!toolCallbacks.isEmpty()) {
                request = request.toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));
            }

            // 使用流式调用收集结果，规避智谱 AI 同步端点不稳定的问题
            String response = collectStreamResponse(request.user(userMessage));

            return ChatResponse.builder()
                    .type(ChatResponseType.TEXT)
                    .content(response)
                    .build();

        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage(), e);
            throw new LlmCallException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatResponse chatWithTools(String userMessage, ConversationContext context,
                                      List<ToolCallback> toolCallbacks) {
        try {
            ChatClient activeClient = resolveChatClient();
            ChatClient.ChatClientRequestSpec request = activeClient.prompt();

            if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
                request = request.system(context.getSystemPrompt());
            }

            if (context.getMessages() != null && !context.getMessages().isEmpty()) {
                request = request.messages(context.getMessages());
            }

            if (!toolCallbacks.isEmpty()) {
                request = request.toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));
            }

            String response = collectStreamResponse(request.user(userMessage));

            return ChatResponse.builder()
                    .type(ChatResponseType.TEXT)
                    .content(response)
                    .build();

        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage(), e);
            throw new LlmCallException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<ChatResponse> chatStream(String userMessage, ConversationContext context) {
        try {
            List<ToolCallback> toolCallbacks = toolRegistry.getToolCallbacks();
            return doStream(userMessage, context, toolCallbacks);
        } catch (Exception e) {
            log.error("LLM 流式调用失败: {}", e.getMessage(), e);
            return Flux.error(new LlmCallException("LLM 流式调用失败: " + e.getMessage(), e));
        }
    }

    @Override
    public Flux<ChatResponse> chatStreamWithTools(String userMessage, ConversationContext context,
                                                  List<ToolCallback> toolCallbacks) {
        try {
            return doStream(userMessage, context, toolCallbacks);
        } catch (Exception e) {
            log.error("LLM 流式调用失败: {}", e.getMessage(), e);
            return Flux.error(new LlmCallException("LLM 流式调用失败: " + e.getMessage(), e));
        }
    }

    private Flux<ChatResponse> doStream(String userMessage, ConversationContext context,
                                        List<ToolCallback> toolCallbacks) {
        ChatClient activeClient = resolveChatClient();
        ChatClient.ChatClientRequestSpec request = activeClient.prompt();

        if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
            request = request.system(context.getSystemPrompt());
        }

        if (context.getMessages() != null && !context.getMessages().isEmpty()) {
            request = request.messages(context.getMessages());
        }

        if (!toolCallbacks.isEmpty()) {
            request = request.toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));
        }

        return request
                .user(userMessage)
                .stream()
                .content()
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(this::isTransientNetworkError)
                        .doBeforeRetry(sig -> log.warn("[LlmGateway] 上游连接瞬时错误,第 {} 次重试: {}",
                                sig.totalRetries() + 1, sig.failure().getMessage())))
                .map(chunk -> ChatResponse.builder()
                        .type(ChatResponseType.TEXT)
                        .content(chunk)
                        .build());
    }

    /**
     * 判断异常是否为上游连接层瞬时错误(可安全重试)。
     * <p>覆盖:
     * <ul>
     *   <li>{@link WebClientRequestException}:连接建立/请求阶段错误(Connection reset/connect timeout 等)</li>
     *   <li>异常消息含 "connection reset"/"connection refused"/"timeout"/"broken pipe"</li>
     * </ul>
     * <p>这类错误发生在 Flux 首个 chunk emit 之前,重试不会导致重复输出,安全。
     */
    private boolean isTransientNetworkError(Throwable error) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof WebClientRequestException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("connection reset")
                        || lower.contains("connection refused")
                        || lower.contains("timeout")
                        || lower.contains("broken pipe")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    @Override
    public String detectIntent(String prompt) {
        try {
            return collectStreamResponse(
                    resolveChatClient().prompt()
                            .system("你是一个意图识别助手。根据用户的输入，从提供的候选列表中选择最合适的一个。只返回对应的 ID，不要返回任何其他内容。")
                            .user(prompt)
            );
        } catch (Exception e) {
            log.warn("意图识别调用失败（将使用默认 Agent）: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String callWithoutTools(String systemPrompt, String userMessage) {
        try {
            ChatClient.ChatClientRequestSpec request = resolveChatClient().prompt();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                request = request.system(systemPrompt);
            }
            return collectStreamResponse(request.user(userMessage));
        } catch (Exception e) {
            log.warn("轻量级 LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过流式调用收集完整响应文本。
     * <p>
     * 智谱 AI 的 Anthropic 兼容 API 同步端点（.call()）不稳定，
     * 会返回 HTTP 400 错误。改用流式调用收集结果来规避此问题。
     */
    private String collectStreamResponse(ChatClient.ChatClientRequestSpec request) {
        StringBuilder sb = new StringBuilder();
        request.stream()
                .content()
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        sb.append(chunk);
                    }
                })
                .blockLast();
        return sb.toString();
    }
}
