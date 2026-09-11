package com.mmcove.agent.rag.config;

import com.mmcove.agent.common.model.entity.KnowledgeBase;
import io.milvus.client.MilvusServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milvus VectorStore 工厂(按知识库构建)。
 *
 * <p>每个知识库绑定一个 Embedding 渠道(同维度) → 对应一个 Milvus collection。
 * 检索时天然按 collection 隔离,无需 metadata filter。
 *
 * <p>MilvusServiceClient 由 spring-ai-starter-vector-store-milvus auto-config 提供;
 * 用 {@link ObjectProvider} 防止 Milvus 未部署时上下文启动失败(开发期可暂不连 Milvus)。
 *
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusVectorStoreFactory {

    private final EmbeddingModelFactory embeddingModelFactory;
    private final ObjectProvider<MilvusServiceClient> milvusServiceClientProvider;

    /** collectionName → VectorStore 缓存 */
    private final Map<String, MilvusVectorStore> cache = new ConcurrentHashMap<>();

    /**
     * 按知识库取 VectorStore(命中缓存直接返回,否则按 collection+渠道+维度创建)。
     * 维度对齐:collection 维度 = 知识库 dimensions = 渠道 dimensions。
     */
    public MilvusVectorStore getVectorStore(KnowledgeBase kb) {
        return cache.computeIfAbsent(kb.getCollectionName(), cn -> create(kb));
    }

    /** 知识库 collection 变更时清缓存(预留) */
    public void invalidate(String collectionName) {
        cache.remove(collectionName);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private MilvusVectorStore create(KnowledgeBase kb) {
        MilvusServiceClient client = milvusServiceClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                    "MilvusServiceClient 不可用(Milvus 未部署或连接失败),无法为知识库 [" + kb.getName() + "] 创建 VectorStore");
        }
        EmbeddingModel embeddingModel = embeddingModelFactory.getEmbeddingModel(kb.getEmbeddingChannel());
        log.info("[MilvusVectorStoreFactory] 创建 VectorStore: collection={}, channel={}",
                kb.getCollectionName(), kb.getEmbeddingChannel());
        // 注:collection 维度由 embeddingModel.dimensions() 自动推断(Spring AI 1.0 Builder 无 dimension 方法)。
        return MilvusVectorStore.builder(client, embeddingModel)
                .collectionName(kb.getCollectionName())
                .databaseName("default")
                .initializeSchema(true)
                .build();
    }
}
