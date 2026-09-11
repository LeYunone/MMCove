package com.mmcove.agent.service;

import com.mmcove.agent.common.model.entity.AiChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 ChatModel 工厂。
 * 根据 AiChannel 配置动态创建 ChatModel 实例并缓存复用。
 */
@Slf4j
@Service
public class DynamicChatModelFactory {

    /**
     * 渠道ID → ChatModel 缓存
     */
    private final Map<Long, ChatModel> channelModelCache = new ConcurrentHashMap<>();

    /**
     * Spring 容器中的系统默认 ChatModel Bean
     */
    private final Map<String, ChatModel> systemChatModels;

    public DynamicChatModelFactory(Map<String, ChatModel> chatModels) {
        this.systemChatModels = chatModels;
        log.info("[DynamicChatModel] 初始化，系统默认 ChatModel: {}", chatModels.keySet());
    }

    /**
     * 根据渠道配置创建或获取缓存的 ChatModel。
     */
    public ChatModel getOrCreate(AiChannel channel) {
        return channelModelCache.computeIfAbsent(channel.getId(), id -> {
            log.info("[DynamicChatModel] 创建 ChatModel: channelId={}, name={}, type={}",
                    channel.getId(), channel.getName(), channel.getType());
            return createChatModel(channel);
        });
    }

    /**
     * 渠道配置更新时清除缓存。
     */
    public void invalidate(Long channelId) {
        ChatModel removed = channelModelCache.remove(channelId);
        if (removed != null) {
            log.info("[DynamicChatModel] 清除缓存: channelId={}", channelId);
        }
    }

    /**
     * 清除全部缓存。
     */
    public void invalidateAll() {
        channelModelCache.clear();
        log.info("[DynamicChatModel] 清除全部缓存");
    }

    /**
     * 获取系统默认 ChatModel（优先 OpenAI，其次取第一个）。
     */
    public ChatModel getSystemDefault() {
        ChatModel openai = systemChatModels.get("openAiChatModel");
        if (openai != null) {
            return openai;
        }
        ChatModel anthropic = systemChatModels.get("anthropicChatModel");
        if (anthropic != null) {
            return anthropic;
        }
        // 兜底：取第一个
        if (!systemChatModels.isEmpty()) {
            return systemChatModels.values().iterator().next();
        }
        throw new IllegalStateException("没有可用的系统默认 ChatModel");
    }

    /**
     * 获取系统默认 ChatModel（按 beanName）。
     */
    public ChatModel getSystemDefault(String beanName) {
        ChatModel model = systemChatModels.get(beanName);
        if (model != null) {
            return model;
        }
        return getSystemDefault();
    }

    /**
     * 获取所有系统 ChatModel Bean 名称。
     */
    public Map<String, ChatModel> getSystemChatModels() {
        return systemChatModels;
    }

    /**
     * 根据渠道类型和配置创建 ChatModel 实例。
     */
    private ChatModel createChatModel(AiChannel channel) {
        String apiKey = channel.getApiKey();
        String baseUrl = channel.getBaseUrl();
        int type = channel.getType() != null ? channel.getType() : AiChannel.TYPE_OPENAI;

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DynamicChatModel] 渠道 {} 无 API Key，回退到系统默认", channel.getName());
            return getSystemDefault();
        }

        String effectiveBaseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : null;

        return switch (type) {
            case AiChannel.TYPE_OPENAI -> createOpenAiChatModel(apiKey, effectiveBaseUrl);
            case AiChannel.TYPE_ANTHROPIC, AiChannel.TYPE_ZHIPU -> createAnthropicChatModel(apiKey, effectiveBaseUrl);
            default -> {
                log.warn("[DynamicChatModel] 未知渠道类型 {}，回退到系统默认", type);
                yield getSystemDefault();
            }
        };
    }

    /**
     * 创建 OpenAI ChatModel。
     * Spring AI 2.0：OpenAiApi 已移除，凭证（apiKey/baseUrl）通过 OpenAiChatOptions 传入。
     */
    private ChatModel createOpenAiChatModel(String apiKey, String baseUrl) {
        OpenAiChatOptions.Builder ob = OpenAiChatOptions.builder();
        if (apiKey != null && !apiKey.isBlank()) {
            ob.apiKey(apiKey);
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            ob.baseUrl(baseUrl);
        }
        return OpenAiChatModel.builder()
                .options(ob.build())
                .build();
    }

    /**
     * 创建 Anthropic ChatModel。
     * Spring AI 2.0：AnthropicApi 已移除，凭证（apiKey/baseUrl）通过 AnthropicChatOptions 传入。
     */
    private ChatModel createAnthropicChatModel(String apiKey, String baseUrl) {
        AnthropicChatOptions.Builder ob = AnthropicChatOptions.builder();
        if (apiKey != null && !apiKey.isBlank()) {
            ob.apiKey(apiKey);
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            ob.baseUrl(baseUrl);
        }
        return AnthropicChatModel.builder()
                .options(ob.build())
                .build();
    }
}
