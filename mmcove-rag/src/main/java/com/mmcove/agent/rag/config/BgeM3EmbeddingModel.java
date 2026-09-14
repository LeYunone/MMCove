package com.mmcove.agent.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BGE-M3 自定义协议 EmbeddingModel(非 OpenAI 兼容端点)。
 *
 * <p>适配 {@code POST {base-url}/embed},入参 {@code {"texts":[...],"return_dense":true}},
 * 响应 {@code {"model":"bge-m3","count":N,"dense_vecs":[[1024个float],...]}}。
 * 免鉴权;延迟毫秒级(自建 GPU 服务)。
 *
 * <p>实现 Spring AI {@link EmbeddingModel} 最小契约:{@code call(EmbeddingRequest)} 供
 * MilvusVectorStore 向量化调用;{@code dimensions()} 供 MilvusVectorStoreFactory 建 collection 定维度。
 *
 * @since 2026-09-14
 */
@Slf4j
public class BgeM3EmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final int dimensions;

    public BgeM3EmbeddingModel(String baseUrl, int dimensions) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.dimensions = dimensions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        Map<String, Object> body = Map.of("texts", texts, "return_dense", true, "return_sparse", false);
        Map<String, Object> resp = restClient.post()
                .uri("/embed")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
        List<List<Number>> denseVecs = resp == null ? null : (List<List<Number>>) resp.get("dense_vecs");
        if (denseVecs == null || denseVecs.size() != texts.size()) {
            throw new IllegalStateException("BGE-M3 响应异常: 期望 " + texts.size()
                    + " 条向量,实际 " + (denseVecs == null ? "null" : denseVecs.size()));
        }
        List<Embedding> embeddings = new ArrayList<>(texts.size());
        for (int i = 0; i < denseVecs.size(); i++) {
            float[] vec = new float[denseVecs.get(i).size()];
            for (int j = 0; j < vec.length; j++) {
                vec[j] = denseVecs.get(i).get(j).floatValue();
            }
            embeddings.add(new Embedding(vec, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(org.springframework.ai.document.Document document) {
        return embed(document.getFormattedContent(org.springframework.ai.document.MetadataMode.EMBED));
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
