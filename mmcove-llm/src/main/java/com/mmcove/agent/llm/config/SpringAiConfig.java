package com.mmcove.agent.llm.config;

import com.mmcove.agent.llm.advisor.TenantContextAdvisor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Spring AI ChatClient 装配配置。
 * 根据 mmcove.ai.model 属性从多个 ChatModel 中选择对应的实例。
 */
@Configuration
@RequiredArgsConstructor
public class SpringAiConfig {

    /**
     * 模型类型 → Spring AI 自动装配的 bean 名称映射。
     * 新增模型时在此扩展即可。
     */
    private static final Map<String, String> MODEL_BEAN_MAP = Map.of(
            "anthropic", "anthropicChatModel",
            "openai", "openAiChatModel"
    );

    private final LlmProperties properties;

    @Bean
    public ChatClient chatClient(Map<String, ChatModel> chatModels,
                                  TenantContextAdvisor tenantContextAdvisor) {
        String modelType = properties.getModel();
        String beanName = MODEL_BEAN_MAP.get(modelType);
        if (beanName == null) {
            throw new IllegalArgumentException("不支持的模型类型: " + modelType
                    + "，支持的类型: " + MODEL_BEAN_MAP.keySet());
        }
        ChatModel chatModel = chatModels.get(beanName);
        if (chatModel == null) {
            throw new IllegalStateException("未找到模型 bean: " + beanName
                    + "，可用的 bean: " + chatModels.keySet());
        }
        return ChatClient.builder(chatModel)
                .defaultAdvisors(tenantContextAdvisor)
                .build();
    }
}
