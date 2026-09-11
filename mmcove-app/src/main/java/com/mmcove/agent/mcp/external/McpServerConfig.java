package com.mmcove.agent.mcp.external;

import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.infra.persistence.repository.KnowledgeDocumentRepository;
import com.mmcove.agent.infra.persistence.repository.ProductLineRepository;
import com.mmcove.agent.mcp.support.McpToolSupport;
import com.mmcove.agent.rag.config.RagProperties;
import com.mmcove.agent.rag.ingest.KnowledgeIngestService;
import com.mmcove.agent.rag.retrieve.KnowledgeRetrieveService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 对外 MCP Server 装配(spring-ai-starter-mcp-server-webmvc,SSE 端点 /sse + /mcp/message)。
 *
 * <p><b>暴露边界</b>:提供容器内唯一的工具规格集合,只含知识库工具。
 * 内部 Agent 的 @Tool 工具由 {@code ToolRegistry}(BeanPostProcessor)私有持有、不进容器,
 * Spring AI MCP 自动配置只收集容器内工具 bean → 内部工具不会外泄。
 * {@code McpKnowledgeTools} 刻意不注册为 Bean(防 ToolRegistry 扫进内部工具池),在此手动 new。
 *
 * <p><b>产品线身份桥</b>:MCP SDK 在会话内部线程执行工具(非 POST servlet 线程),
 * 工具线程读不到 TokenAuthContext。包装层从 exchange 解析会话 →
 * {@link McpSessionIdentityHolder} 查该会话 token 绑定的产品线 → 写入工具线程的
 * TokenAuthContext(用完清空),KbAccessService 隔离策略因此照常生效。
 *
 * <p>端点鉴权:{@code AuthInterceptor.isMcpPath}(只认 Bearer sk- token)。
 *
 * @since 2026-09-07
 */
@Configuration
public class McpServerConfig {

    /**
     * 对外 MCP 工具的原生 MCP 规格(知识库 + Vibe 流水线,经 Spring AI 自动配置装配进 server)。
     * 不用 ToolCallbackProvider bean —— 那样拿不到 exchange,无法做会话身份桥接。
     * 新工具对象一律在此追加(不新建第二个 List bean,避免自动配置对多 bean 的收集行为差异)。
     */
    @Bean
    public List<McpServerFeatures.SyncToolSpecification> knowledgeMcpToolSpecifications(
            KbAccessService kbAccessService,
            KnowledgeRetrieveService knowledgeRetrieveService,
            KnowledgeIngestService knowledgeIngestService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ProductLineRepository productLineRepository,
            RagProperties ragProperties,
            McpSessionIdentityHolder identityHolder,
            com.mmcove.agent.vibe.VibePromptComposerService vibePromptComposerService,
            com.mmcove.agent.vibe.VibeRunService vibeRunService,
            com.mmcove.agent.vibe.PipelineRegistry pipelineRegistry) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new McpKnowledgeTools(kbAccessService, knowledgeRetrieveService,
                                knowledgeIngestService, knowledgeDocumentRepository, productLineRepository,
                                ragProperties),
                        new McpVibeTools(vibePromptComposerService, vibeRunService, pipelineRegistry, kbAccessService))
                .build()
                .getToolCallbacks();

        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();
        for (ToolCallback callback : callbacks) {
            ToolDefinition def = callback.getToolDefinition();
            java.util.Map<String, Object> inputSchema;
            try {
                inputSchema = McpToolSupport.MAPPER.readValue(
                        def.inputSchema(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() { });
            } catch (Exception e) {
                inputSchema = java.util.Map.of("type", "object");
            }
            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name(def.name())
                    .description(def.description())
                    .inputSchema(inputSchema)
                    .build();
            specs.add(new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> {
                Long productLineId = identityHolder.resolveLine(exchange.sessionId());
                TokenAuthContext.get().setProductLineId(productLineId);
                // 归属调用方 token id 一并桥接(vibe run 归属隔离:防同产品线跨调用方劫持恢复兜底)
                TokenAuthContext.get().setTokenId(identityHolder.resolveOwnerId(exchange.sessionId()));
                try {
                    String toolInput = McpToolSupport.MAPPER.writeValueAsString(args);
                    String result = callback.call(toolInput);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)), false, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(McpToolSupport.err("工具执行失败: " + e.getMessage()))), true, null, null);
                } finally {
                    // 会话线程复用于其他调用方时不能串身份,用完即清
                    TokenAuthContext.get().setProductLineId(null);
                    TokenAuthContext.get().setTokenId(null);
                }
            }));
        }
        return specs;
    }
}
