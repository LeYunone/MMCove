package com.mmcove.agent.rag.config;

import lombok.Data;

/**
 * Embedding 渠道配置(动态多渠道向量化)。
 *
 * <p>一个渠道 = 一个 Embedding 模型接入点(provider + 端点 + 模型名 + 维度)。
 * {@code knowledge_base.embedding_channel} 绑定某个 channel name,
 * 入库/检索时由 {@link EmbeddingModelFactory} 取对应模型实例。
 *
 * @since 2026-08-03
 */
@Data
public class EmbeddingChannel {

    /** 渠道名(唯一标识,如 zhipu / local-bge),knowledge_base.embedding_channel 引用此名 */
    private String name;

    /** provider 类型:openai-compat(OpenAI 兼容端点)/ onnx(本地 ONNX,暂未实现) */
    private String provider = "openai-compat";

    /** OpenAI 兼容端点的 base-url(如 https://open.bigmodel.cn/api/paas/v4) */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名(如 embedding-3) */
    private String model;

    /** 向量维度(必须与 Milvus collection 维度一致) */
    private int dimensions;
}
