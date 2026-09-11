package com.mmcove.agent.rag.ingest;

import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.common.model.entity.KnowledgeChunk;
import com.mmcove.agent.common.model.entity.KnowledgeDocument;
import com.mmcove.agent.infra.persistence.repository.KnowledgeChunkRepository;
import com.mmcove.agent.infra.persistence.repository.KnowledgeDocumentRepository;
import com.mmcove.agent.rag.config.MilvusVectorStoreFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识入库服务。
 *
 * <p>流程:写 document(待入库) → 切片 → 写 Milvus(向量) → 回表 knowledge_chunk(原文/审计) → 更新状态。
 *
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final MilvusVectorStoreFactory milvusVectorStoreFactory;
    private final ChunkSplitter chunkSplitter;

    /**
     * 入库一篇文档。
     *
     * @param kb      所属知识库(决定 collection + Embedding 渠道)
     * @param title   文档标题
     * @param content 文档正文
     * @param mime    MIME 类型(markdown/pdf/docx)
     * @return 创建的 document id
     */
    public Long ingest(KnowledgeBase kb, String title, String content, String mime) {
        // 1. 写 document(待入库)
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kb.getId());
        doc.setTitle(title);
        doc.setSourceUrl(title);
        doc.setMime(mime);
        doc.setStatus(0);
        doc.setTenantId(kb.getTenantId());
        knowledgeDocumentRepository.insert(doc);

        try {
            // 2. 切片
            List<Document> chunks = chunkSplitter.split(List.of(new Document(content)));

            // 3. 写 Milvus(向量)
            MilvusVectorStore vectorStore = milvusVectorStoreFactory.getVectorStore(kb);
            vectorStore.add(chunks);

            // 4. 回表 knowledge_chunk(原文/审计;vectorId 留空,MVP 检索走 Milvus 返回内容不回表)
            int seq = 0;
            for (Document chunk : chunks) {
                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocId(doc.getId());
                kc.setSeq(seq++);
                kc.setContent(chunk.getText());
                kc.setTokenCount(chunk.getText().length() / 4);
                knowledgeChunkRepository.insert(kc);
            }

            knowledgeDocumentRepository.updateStatus(doc.getId(), 1);
            log.info("文档入库成功: kb={}, docId={}, title={}, chunks={}",
                    kb.getName(), doc.getId(), title, chunks.size());
            return doc.getId();
        } catch (Exception e) {
            log.error("文档入库失败: kb={}, docId={}, err={}", kb.getName(), doc.getId(), e.getMessage(), e);
            knowledgeDocumentRepository.updateStatus(doc.getId(), 2);
            throw new RuntimeException("文档入库失败: " + e.getMessage(), e);
        }
    }
}
