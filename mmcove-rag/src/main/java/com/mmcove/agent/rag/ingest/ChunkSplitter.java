package com.mmcove.agent.rag.ingest;

import com.mmcove.agent.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档切片器:按字符数切片 + 重叠(避免语义断裂)。
 *
 * <p>简单实现,不依赖外部 Tokenizer;后续可换 Spring AI {@code TokenTextSplitter} 做精准 token 切分。
 *
 * @since 2026-08-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkSplitter {

    private final RagProperties properties;

    public List<Document> split(List<Document> documents) {
        int chunkSize = Math.max(100, properties.getChunkSize());
        int overlap = Math.min(properties.getChunkOverlap(), chunkSize / 2);
        List<Document> chunks = new ArrayList<>();
        for (Document doc : documents) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            int start = 0;
            int seq = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                String chunkText = text.substring(start, end);
                Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                metadata.put("chunk_seq", seq);
                chunks.add(new Document(chunkText, metadata));
                seq++;
                if (end >= text.length()) {
                    break;
                }
                start += chunkSize - overlap;
            }
        }
        log.debug("切片完成: 输入 {} 篇文档 → {} 个切片", documents.size(), chunks.size());
        return chunks;
    }
}
