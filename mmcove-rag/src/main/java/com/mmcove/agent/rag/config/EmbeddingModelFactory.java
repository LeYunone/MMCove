package com.mmcove.agent.rag.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 EmbeddingModel 工厂。
 *
 * <p>按 channel 名(配置于 {@code mmcove.rag.embedding.channels})创建/缓存 EmbeddingModel 实例。
 * 复刻自 {@code com.mmcove.agent.service.DynamicChatModelFactory} 的模式
 * (ConcurrentHashMap + computeIfAbsent + switch provider)。
 *
 * <p><b>维度对齐硬约束</b>:一个 Milvus collection ↔ 一个 Embedding 渠道(同维度)。
 * 不同渠道维度不同,不可混用同一 collection。
 *
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingModelFactory {

    private final EmbeddingProperties properties;

    /** channel name → EmbeddingModel 缓存 */
    private final Map<String, EmbeddingModel> cache = new ConcurrentHashMap<>();

    /**
     * 按渠道名取 EmbeddingModel(命中缓存直接返回,否则按配置创建)。
     *
     * @param channelName 渠道名,null/空 回退默认渠道
     */
    public EmbeddingModel getEmbeddingModel(String channelName) {
        String name = resolveChannelName(channelName);
        return cache.computeIfAbsent(name, this::createEmbeddingModel);
    }

    /**
     * 取渠道对应的向量维度。
     */
    public int getDimensions(String channelName) {
        return requireChannel(resolveChannelName(channelName)).getDimensions();
    }

    /** 渠道配置变更时清缓存(预留) */
    public void invalidate(String channelName) {
        cache.remove(channelName);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private String resolveChannelName(String channelName) {
        return (channelName == null || channelName.isBlank()) ? properties.getDefaultChannel() : channelName;
    }

    private EmbeddingChannel requireChannel(String name) {
        return properties.getChannels().stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未找到 Embedding 渠道配置: " + name + ",已配置渠道: "
                                + properties.getChannels().stream().map(EmbeddingChannel::getName).toList()));
    }

    private EmbeddingModel createEmbeddingModel(String channelName) {
        EmbeddingChannel ch = requireChannel(channelName);
        log.info("[EmbeddingModelFactory] 创建 EmbeddingModel: channel={}, provider={}, model={}, dim={}",
                ch.getName(), ch.getProvider(), ch.getModel(), ch.getDimensions());
        return switch (ch.getProvider()) {
            case "openai-compat" -> createOpenAiCompatEmbeddingModel(ch);
            case "bge-m3" -> new BgeM3EmbeddingModel(ch.getBaseUrl(), ch.getDimensions());
            case "onnx" -> throw new UnsupportedOperationException(
                    "ONNX 本地 Embedding 渠道暂未实现(MVP 仅支持 openai-compat): " + channelName);
            default -> throw new IllegalArgumentException(
                    "不支持的 Embedding provider: " + ch.getProvider() + "(渠道 " + channelName + ")");
        };
    }

    /**
     * 创建 OpenAI 兼容端点的 EmbeddingModel(智谱 embedding-3 等走此路径)。
     * Spring AI 2.0：OpenAiApi 已移除，凭证（apiKey/baseUrl）通过 OpenAiEmbeddingOptions 传入，
     * 由模型据此构建底层 SDK 客户端。
     */
    private EmbeddingModel createOpenAiCompatEmbeddingModel(EmbeddingChannel ch) {
        OpenAiEmbeddingOptions.Builder ob = OpenAiEmbeddingOptions.builder().model(ch.getModel());
        if (ch.getApiKey() != null && !ch.getApiKey().isBlank()) {
            ob.apiKey(ch.getApiKey());
        }
        if (ch.getBaseUrl() != null && !ch.getBaseUrl().isBlank()) {
            ob.baseUrl(ch.getBaseUrl());
        }
        return new OpenAiEmbeddingModel(MetadataMode.EMBED, ob.build());
    }
}
