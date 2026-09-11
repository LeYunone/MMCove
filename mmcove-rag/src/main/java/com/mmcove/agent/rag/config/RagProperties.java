package com.mmcove.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 领域配置(mmcove.rag)。
 *
 * @since 2026-08-03
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmcove.rag")
public class RagProperties {

    /** 检索召回的 top-k 切片数 */
    private int topK = 5;

    /** 文档切片目标 token 数 */
    private int chunkSize = 800;

    /** 切片重叠 token 数(避免语义断裂) */
    private int chunkOverlap = 200;
    /** MCP 工具上传文档的解码后内容上限(字节;base64 解码后校验;大文件引导走管理界面 multipart) */
    private long mcpUploadMaxBytes = 4L * 1024 * 1024;

}
