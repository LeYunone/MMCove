package com.mmcove.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 动态渠道配置(mmcove.rag.embedding)。
 *
 * <p>新增 Embedding 模型只需在此追加一条 channel,无需改代码。
 *
 * @since 2026-08-03
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmcove.rag.embedding")
public class EmbeddingProperties {

    /** 默认渠道名(未显式指定渠道的知识库/检索回退到此) */
    private String defaultChannel = "zhipu";

    /** 可用渠道列表 */
    private List<EmbeddingChannel> channels = new ArrayList<>();
}
