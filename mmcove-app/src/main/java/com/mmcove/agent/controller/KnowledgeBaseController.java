package com.mmcove.agent.controller;

import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.common.model.entity.KnowledgeDocument;
import com.mmcove.agent.infra.persistence.repository.AgentKnowledgeBaseRepository;
import com.mmcove.agent.infra.persistence.repository.KnowledgeBaseRepository;
import com.mmcove.agent.infra.persistence.repository.KnowledgeDocumentRepository;
import com.mmcove.agent.rag.config.EmbeddingModelFactory;
import com.mmcove.agent.rag.ingest.KnowledgeIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理控制器(用户自助)。
 *
 * <p>知识库 CRUD + 文档上传(触发入库) + Agent 关联配置(对标 ToolDefinitionController 的分组管理)。
 * 隔离维度为 Agent→库 关联(agent_knowledge_base),不依赖未启用的 TenantContext。
 * 鉴权靠 {@code TokenAuthInterceptor}(API Token),无需权限注解。
 *
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final AgentKnowledgeBaseRepository agentKnowledgeBaseRepository;
    private final KnowledgeIngestService knowledgeIngestService;
    private final EmbeddingModelFactory embeddingModelFactory;

    // ==================== 知识库 CRUD ====================

    /** 列表:所有启用的知识库 */
    @GetMapping
    public ApiResponse<List<KnowledgeBase>> list() {
        return ApiResponse.success(knowledgeBaseRepository.findAllEnabled());
    }

    /** 创建知识库(自动按渠道设定维度 + 生成 collection 名) */
    @PostMapping
    public ApiResponse<KnowledgeBase> create(@RequestBody KnowledgeBase kb) {
        // 维度对齐:collection 维度 = 渠道维度(硬约束)
        kb.setDimensions(embeddingModelFactory.getDimensions(kb.getEmbeddingChannel()));
        kb.setCollectionName(generateCollectionName(kb.getName()));
        kb.setStatus(1);
        if (kb.getPriority() == null) {
            kb.setPriority(0);
        }
        knowledgeBaseRepository.insert(kb);
        return ApiResponse.success(kb);
    }

    /** 更新知识库(描述/关键词/场景标签/优先级;渠道与 collection 不可改 —— 维度绑定) */
    @PutMapping("/{id:\\d+}")
    public ApiResponse<KnowledgeBase> update(@PathVariable Long id, @RequestBody KnowledgeBase kb) {
        return knowledgeBaseRepository.findById(id)
                .map(existing -> {
                    kb.setId(id);
                    kb.setCreatedAt(existing.getCreatedAt());
                    // 渠道/collection/维度 不可改(维度对齐硬约束)
                    kb.setEmbeddingChannel(existing.getEmbeddingChannel());
                    kb.setCollectionName(existing.getCollectionName());
                    kb.setDimensions(existing.getDimensions());
                    knowledgeBaseRepository.update(kb);
                    return ApiResponse.success(kb);
                })
                .orElse(ApiResponse.error(404, "知识库不存在"));
    }

    /** 删除知识库(MVP 仅删元数据;Milvus collection 级联清理待后续) */
    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseRepository.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== 文档管理 ====================

    /** 上传文档(触发入库:切片 → Embedding → Milvus → 回表) */
    @PostMapping("/{id:\\d+}/documents")
    public ApiResponse<Map<String, Long>> uploadDocument(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestBody String content,
            @RequestParam(defaultValue = "markdown") String mime) {
        return knowledgeBaseRepository.findById(id)
                .map(kb -> {
                    Long docId = knowledgeIngestService.ingest(kb, title, content, mime);
                    return ApiResponse.success(Map.of("docId", docId));
                }).orElse(ApiResponse.error(404, "知识库不存在"));
    }

    /** 文档列表(含入库状态) */
    @GetMapping("/{id:\\d+}/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments(@PathVariable Long id) {
        return ApiResponse.success(knowledgeDocumentRepository.findByKbId(id));
    }

    /** 删除文档(MVP 仅删元数据;向量清理待后续) */
    @DeleteMapping("/{id:\\d+}/documents/{docId:\\d+}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id, @PathVariable Long docId) {
        knowledgeDocumentRepository.deleteById(docId);
        return ApiResponse.success();
    }

    // ==================== Agent ↔ 知识库 关联 ====================

    /** 查询某 Agent 关联的知识库 id 列表 */
    @GetMapping("/agent-bindings/{agentId}")
    public ApiResponse<List<Long>> getAgentBindings(@PathVariable String agentId) {
        return ApiResponse.success(agentKnowledgeBaseRepository.findEnabledKbIdsByAgent(agentId));
    }

    /** 全量设置某 Agent 关联的知识库(多选保存,用户决定关联几个) */
    @PutMapping("/agent-bindings/{agentId}")
    public ApiResponse<Void> setAgentBindings(@PathVariable String agentId,
                                              @RequestBody Map<String, List<Long>> body) {
        List<Long> kbIds = body.get("kbIds");
        agentKnowledgeBaseRepository.replaceAll(agentId, kbIds == null ? List.of() : kbIds);
        return ApiResponse.success();
    }

    /** 生成 Milvus collection 名(全局唯一,符合 Milvus 命名规范) */
    private String generateCollectionName(String name) {
        String safe = name == null ? "default" : name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        return "mmcove_kb_" + safe;
    }
}
