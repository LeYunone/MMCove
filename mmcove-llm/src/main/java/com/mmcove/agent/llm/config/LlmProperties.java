package com.mmcove.agent.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性，从 YAML 配置文件绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmcove.ai")
public class LlmProperties {

    private String model = "openai";
    private double temperature = 0.7;
    private int maxTokens = 4096;
    private boolean streaming = true;
}
