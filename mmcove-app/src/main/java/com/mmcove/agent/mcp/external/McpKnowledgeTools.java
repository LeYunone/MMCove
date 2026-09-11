package com.mmcove.agent.mcp.external;

import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.common.model.entity.KnowledgeDocument;
import com.mmcove.agent.common.model.entity.ProductLine;
import com.mmcove.agent.infra.persistence.repository.KnowledgeDocumentRepository;
import com.mmcove.agent.infra.persistence.repository.ProductLineRepository;
import com.mmcove.agent.mcp.support.McpToolSupport;
import com.mmcove.agent.rag.config.RagProperties;
import com.mmcove.agent.rag.ingest.KnowledgeIngestService;
import com.mmcove.agent.rag.retrieve.KnowledgeRetrieveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对外知识库 MCP 工具集(经标准 MCP 协议暴露给 Claude Code/Cursor 等外部 AI 客户端)。
 *
 * <p><b>刻意不注册为 Spring Bean</b>:在 {@code McpServerConfig} 里手动 new。
 * 若注册为 Bean,内部 {@code ToolRegistry}(BeanPostProcessor)会扫进内部 Agent 工具池,
 * 且 AgentOrchestrator 分组无配置时回退挂全量 → 外部工具泄漏给内部 Agent。
 *
 * <p>产品线交互(用户话术驱动,不暴露数字 ID):
 * <ul>
 *   <li>默认产品线:MCP 客户端配置的 X-Product-Line 请求头(各自配置各自的产品线名),
 *       查询/上传等一切行为默认使用它;</li>
 *   <li>话术带产品线名称(如"查一下物联产品线的知识"):查询范围 = 指定线 + 默认线 + 共享库合并;</li>
 *   <li>上传:目标库属于默认线直接执行;属于话术指定的其他线 → 先询问用户,确认后带 confirm 重调。</li>
 * </ul>
 *
 * <p>入参全 String(规避 MethodToolCallback 数值转换 NPE,内部用 McpToolSupport.toLong 容错解析)。
 *
 * @since 2026-09-07
 */
@Slf4j
@RequiredArgsConstructor
public class McpKnowledgeTools {

    /** searchKnowledgeBase 默认与最大 topK */
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    /** 多库检索的返回切片总数上限(防大范围检索撑爆上下文) */
    private static final int MAX_TOTAL_CHUNKS = 50;

    private final KbAccessService kbAccessService;
    private final KnowledgeRetrieveService knowledgeRetrieveService;
    private final KnowledgeIngestService knowledgeIngestService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProductLineRepository productLineRepository;
    private final RagProperties ragProperties;

    /** 列出全部产品线(知识库产品线体系的顶层目录) */
    @Tool(description = "列出全部产品线目录。当用户想看有哪些产品线、或不确定产品线名称时调用。"
            + "返回字段:id、code(编码)、name(名称)、description(描述)、status(1启用)。")
    public String listProductLines() {
        List<Map<String, Object>> lines = productLineRepository.findAll().stream()
                .map(this::lineSummary)
                .collect(Collectors.toList());
        return McpToolSupport.ok("共 " + lines.size() + " 条产品线", lines);
    }

    /** 列出当前可读的知识库(默认线+共享;带产品线名称则与默认线合并) */
    @Tool(description = "列出当前可访问的知识库。默认返回「默认产品线」的知识库 + 全部共享知识库;"
            + "用户提到具体产品线名称时传 productLineName,返回「指定产品线 + 默认产品线 + 共享库」的合并结果。"
            + "返回字段:id、name、description、productLineName(归属产品线)、isShared(是否共享库)、dimensions。")
    public String listKnowledgeBases(
            @ToolParam(description = "产品线名称(可选;用户话术提到的产品线,如'示例产品线'或简称)", required = false) String productLineName) {
        return guard(() -> {
            List<KnowledgeBase> kbs = kbAccessService.readableKnowledgeBases(productLineName);
            Map<Long, String> lineNames = productLineRepository.findAll().stream()
                    .collect(Collectors.toMap(ProductLine::getId, ProductLine::getName, (a, b) -> a));
            List<Map<String, Object>> data = kbs.stream()
                    .map(kb -> kbSummary(kb, lineNames))
                    .collect(Collectors.toList());
            return McpToolSupport.ok("共 " + data.size() + " 个知识库"
                    + (productLineName == null || productLineName.isBlank()
                        ? "(默认产品线 + 共享库)" : "(产品线[" + productLineName + "] + 默认产品线 + 共享库)"), data);
        });
    }

    /** 列出知识库下的文档目录 */
    @Tool(description = "列出指定知识库下的全部文档(含入库状态)。当用户想看某知识库里有什么资料、确认文档是否已上传时调用。"
            + "入参 kbId 来自 listKnowledgeBases。返回字段:id、title、mime、status(0待入库/1已入库/2失败)、createdAt。")
    public String listDocuments(
            @ToolParam(description = "知识库ID(来自 listKnowledgeBases)") String kbId) {
        return guard(() -> {
            Long id = requireId(kbId);
            kbAccessService.requireReadable(id, null);
            List<Map<String, Object>> docs = knowledgeDocumentRepository.findByKbId(id).stream()
                    .map(this::docSummary)
                    .collect(Collectors.toList());
            return McpToolSupport.ok("知识库[" + id + "] 共 " + docs.size() + " 篇文档", docs);
        });
    }

    /** 检索知识库(核心工具) */
    @Tool(description = "检索知识库,返回与问题相关的文档切片。当用户问到业务知识、产品手册、操作指南、规范、FAQ 等内容时调用。"
            + "默认检索范围 = 默认产品线(MCP 配置)的知识库 + 全部共享知识库;"
            + "用户提到具体产品线名称(如'查物联产品线的资料')时传 productLineName,"
            + "范围变为「指定产品线 + 默认产品线 + 共享库」合并检索;传 kbId 精确检索某个库;topK 为每个库的召回切片数(默认5,最大20)。")
    public String searchKnowledgeBase(
            @ToolParam(description = "检索问题/关键词") String query,
            @ToolParam(description = "知识库ID(可选;精确检索某个库,来自 listKnowledgeBases)", required = false) String kbId,
            @ToolParam(description = "产品线名称(可选;用户话术提到的产品线,与默认产品线合并检索)", required = false) String productLineName,
            @ToolParam(description = "每个库的召回切片数(可选;默认5,最大20)", required = false) String topK) {
        return guard(() -> {
            if (McpToolSupport.isBlank(query)) {
                return McpToolSupport.err("query 不能为空");
            }
            int k = McpToolSupport.toLong(topK) == null ? DEFAULT_TOP_K
                    : Math.min(Math.max(McpToolSupport.toLong(topK).intValue(), 1), MAX_TOP_K);
            Long targetKb = McpToolSupport.toLong(kbId);

            // 路径A:指定 kbId 单库检索
            if (targetKb != null) {
                KnowledgeBase kb = kbAccessService.requireReadable(targetKb, productLineName);
                List<Document> hits = knowledgeRetrieveService.retrieveDocuments(kb, query, k);
                return resultText(query, Map.of(kb.getName(), hits));
            }

            // 路径B:默认范围(默认线+共享)或合并范围(指定线+默认线+共享)逐库检索
            List<KnowledgeBase> scope = kbAccessService.readableKnowledgeBases(productLineName);
            if (scope.isEmpty()) {
                return McpToolSupport.err("当前无可检索的知识库(MCP 未配置默认产品线且无共享库)");
            }
            Map<String, List<Document>> grouped = new LinkedHashMap<>();
            int total = 0;
            for (KnowledgeBase kb : scope) {
                if (total >= MAX_TOTAL_CHUNKS) {
                    break;
                }
                List<Document> hits = knowledgeRetrieveService.retrieveDocuments(kb, query, k);
                if (!hits.isEmpty()) {
                    grouped.put(kb.getName(), hits);
                    total += hits.size();
                }
            }
            if (grouped.isEmpty()) {
                return McpToolSupport.ok("未检索到相关内容(范围:" + scope.size() + " 个知识库): " + query, List.of());
            }
            return resultText(query, grouped);
        });
    }

    /** 上传文档到知识库(默认线直接执行;跨线先询问) */
    @Tool(description = "上传一篇文档到指定知识库(向量化入库)。当用户要把资料、文档、笔记、本次对话生成的文案存入知识库时调用。"
            + "目标库属于默认产品线时直接执行;属于用户话术指定的其他产品线时会返回确认提示,"
            + "须向用户确认后携带 confirm=yes 重新调用才会上传。"
            + "content 为文档正文;二进制格式(pdf/docx)须用 contentEncoding=base64 传文件内容,纯文本(markdown/txt)直接传原文。"
            + "解码后上限 4MB,更大文件请走管理界面上传。")
    public String uploadDocument(
            @ToolParam(description = "目标知识库ID(来自 listKnowledgeBases)") String kbId,
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "文档正文(纯文本)或文件内容(base64 编码)") String content,
            @ToolParam(description = "内容编码:text(默认,纯文本)或 base64(pdf/docx 等二进制)", required = false) String contentEncoding,
            @ToolParam(description = "MIME 类型(可选;markdown/text/plain/pdf/docx,默认按标题扩展名推断)", required = false) String mime,
            @ToolParam(description = "产品线名称(可选;用户话术指定的目标产品线,用于跨线上传时的确认判断)", required = false) String productLineName,
            @ToolParam(description = "跨产品线上传的用户确认:yes=用户已确认(第一次调用被要求确认后,确认了才带 yes 重调)", required = false) String confirm) {
        return guard(() -> {
            Long id = requireId(kbId);
            if (McpToolSupport.isBlank(title) || McpToolSupport.isBlank(content)) {
                return McpToolSupport.err("title 与 content 不能为空");
            }
            boolean confirmed = "yes".equalsIgnoreCase(confirm == null ? "" : confirm.trim())
                    || "true".equalsIgnoreCase(confirm == null ? "" : confirm.trim());
            KnowledgeBase kb = kbAccessService.requireWritable(id, productLineName, confirmed);

            String text = content;
            String encoding = contentEncoding == null || contentEncoding.isBlank()
                    ? "text" : contentEncoding.trim().toLowerCase();
            if ("base64".equals(encoding)) {
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(content.trim());
                } catch (IllegalArgumentException e) {
                    return McpToolSupport.err("content 不是合法的 base64 内容");
                }
                if (bytes.length > ragProperties.getMcpUploadMaxBytes()) {
                    return McpToolSupport.err("文档内容超过上限 " + (ragProperties.getMcpUploadMaxBytes() / 1024 / 1024)
                            + "MB,请改走管理界面上传(支持 20MB)");
                }
                text = new String(bytes, StandardCharsets.UTF_8);
            } else if (content.getBytes(StandardCharsets.UTF_8).length > ragProperties.getMcpUploadMaxBytes()) {
                return McpToolSupport.err("文档内容超过上限 " + (ragProperties.getMcpUploadMaxBytes() / 1024 / 1024)
                        + "MB,请改走管理界面上传(支持 20MB)");
            }
            String docMime = resolveMime(mime, title);
            try {
                Long docId = knowledgeIngestService.ingest(kb, title.trim(), text, docMime);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("docId", docId);
                data.put("kb", kb.getName());
                data.put("status", 1);
                return McpToolSupport.ok("文档已入库", data);
            } catch (Exception e) {
                log.error("MCP 上传文档入库失败: kb={}, title={}, err={}", kb.getName(), title, e.getMessage(), e);
                return McpToolSupport.err("文档入库失败: " + e.getMessage());
            }
        });
    }

    // ==================== 私有助手 ====================

    /** 统一异常护栏:隔离校验转 err 文案、跨线确认转询问文案,不向 MCP 客户端抛栈 */
    private String guard(java.util.function.Supplier<String> action) {
        try {
            return action.get();
        } catch (NeedConfirmException e) {
            return McpToolSupport.err(e.getMessage());
        } catch (IllegalArgumentException e) {
            return McpToolSupport.err(e.getMessage());
        } catch (Exception e) {
            log.error("MCP 知识库工具执行失败: {}", e.getMessage(), e);
            return McpToolSupport.err("执行失败: " + e.getMessage());
        }
    }

    private static Long requireId(String raw) {
        Long id = McpToolSupport.toLong(raw);
        if (id == null) {
            throw new IllegalArgumentException("ID 不合法: " + raw);
        }
        return id;
    }

    /** 组检索结果:按库分组拼接(【库:名】+ 编号切片) */
    private String resultText(String query, Map<String, List<Document>> grouped) {
        StringBuilder sb = new StringBuilder("以下是知识库检索到的相关内容(问题: ")
                .append(query).append("),请据此回答用户:\n\n");
        int total = 0;
        for (Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            sb.append("【库: ").append(entry.getKey()).append("】\n");
            int i = 1;
            for (Document doc : entry.getValue()) {
                sb.append("--- 切片 ").append(i++).append(" ---\n")
                        .append(doc.getText()).append("\n\n");
                total++;
            }
        }
        return sb.toString() + "\n(共 " + total + " 个切片)";
    }

    /** mime 解析:显式传值 > 标题扩展名推断 > 默认 markdown */
    private static String resolveMime(String mime, String title) {
        if (mime != null && !mime.isBlank()) {
            return mime.trim();
        }
        String lower = title == null ? "" : title.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "markdown";
    }

    private Map<String, Object> lineSummary(ProductLine line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", line.getId());
        m.put("code", line.getCode());
        m.put("name", line.getName());
        m.put("description", line.getDescription());
        m.put("status", line.getStatus());
        return m;
    }

    private Map<String, Object> kbSummary(KnowledgeBase kb, Map<Long, String> lineNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", kb.getId());
        m.put("name", kb.getName());
        m.put("description", kb.getDescription());
        m.put("productLineId", kb.getProductLineId());
        m.put("productLineName", kb.getProductLineId() == null ? null : lineNames.get(kb.getProductLineId()));
        m.put("isShared", Boolean.TRUE.equals(kb.getIsShared()));
        m.put("dimensions", kb.getDimensions());
        return m;
    }

    private Map<String, Object> docSummary(KnowledgeDocument doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("mime", doc.getMime());
        m.put("status", doc.getStatus());
        m.put("createdAt", doc.getCreatedAt());
        return m;
    }
}
