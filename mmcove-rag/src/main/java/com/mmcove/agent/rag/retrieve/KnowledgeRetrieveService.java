package com.mmcove.agent.rag.retrieve;

import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.infra.persistence.repository.KnowledgeBaseRepository;
import com.mmcove.agent.rag.config.MilvusVectorStoreFactory;
import com.mmcove.agent.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识检索服务。
 *
 * <p>按知识库取 VectorStore(每库一 collection) → 向量召回 top-k → 拼上下文。
 * 检索天然按 collection 隔离,无需 metadata filter。
 *
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrieveService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final MilvusVectorStoreFactory milvusVectorStoreFactory;
    private final RagProperties ragProperties;

    /** 按 kbId 检索 */
    public String retrieve(Long kbId, String query) {
        return knowledgeBaseRepository.findById(kbId)
                .map(kb -> retrieve(kb, query))
                .orElseGet(() -> {
                    log.warn("知识库不存在: {}", kbId);
                    return "";
                });
    }

    /** 按 KnowledgeBase 检索(已有 entity 时免一次查询) */
    public String retrieve(KnowledgeBase kb, String query) {
        MilvusVectorStore vectorStore = milvusVectorStoreFactory.getVectorStore(kb);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(ragProperties.getTopK())
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);
        if (docs == null || docs.isEmpty()) {
            log.info("知识库 [{}] 未召回相关切片: query={}", kb.getName(), query);
            return "";
        }
        log.info("知识库 [{}] 召回 {} 个切片: query={}", kb.getName(), docs.size(), query);
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }
    /** 带 topK 检索并返回 Document 列表(多库检索需自定义 topK 与结构化结果) */
    public java.util.List<org.springframework.ai.document.Document> retrieveDocuments(
            KnowledgeBase kb, String query, int topK) {
        org.springframework.ai.vectorstore.milvus.MilvusVectorStore vectorStore =
                milvusVectorStoreFactory.getVectorStore(kb);
        org.springframework.ai.vectorstore.SearchRequest request = org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        java.util.List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(request);
        if (docs == null || docs.isEmpty()) {
            log.info("知识库 [{}] 未召回相关切片: query={}", kb.getName(), query);
            return java.util.List.of();
        }
        log.info("知识库 [{}] 召回 {} 个切片: query={}", kb.getName(), docs.size(), query);
        return docs;
    }

    /**
     * 带相似度门槛的评分检索(Vibe 提示词工厂锚点知识用)。
     *
     * <p>低于 minScore 的切片剔除(噪音知识反向劣化提示词,宁缺毋滥),
     * 按 score 降序返回(Milvus COSINE:score 越高越相关)。
     */
    public java.util.List<org.springframework.ai.document.Document> retrieveScored(
            KnowledgeBase kb, String query, int topK, double minScore) {
        org.springframework.ai.vectorstore.milvus.MilvusVectorStore vectorStore =
                milvusVectorStoreFactory.getVectorStore(kb);
        org.springframework.ai.vectorstore.SearchRequest request = org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(minScore)
                .build();
        java.util.List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(request);
        if (docs == null || docs.isEmpty()) {
            log.info("知识库 [{}] 未召回达标切片: query={}, minScore={}", kb.getName(), query, minScore);
            return java.util.List.of();
        }
        log.info("知识库 [{}] 召回 {} 个达标切片: query={}, minScore={}",
                kb.getName(), docs.size(), query, minScore);
        return docs.stream()
                .filter(d -> d.getScore() != null && d.getScore() >= minScore)
                .sorted(java.util.Comparator.comparing(
                        org.springframework.ai.document.Document::getScore).reversed())
                .toList();
    }

}
