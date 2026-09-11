package com.mmcove.agent.controller;

import com.mmcove.agent.common.context.TokenAuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容接口控制器。
 * 提供 /v1/models 等 OpenAI 兼容接口。
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenAiCompatController {

    /** 支持的模型列表 */
    private static final List<Map<String, Object>> SUPPORTED_MODELS = List.of(
            createModel("gpt-4", "GPT-4", "text", 8192, 4096),
            createModel("gpt-4-32k", "GPT-4 32K", "text", 32768, 4096),
            createModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "text", 16385, 4096),
            createModel("gpt-3.5-turbo-16k", "GPT-3.5 Turbo 16K", "text", 16385, 4096),
            createModel("claude-3-opus", "Claude 3 Opus", "text", 200000, 4096),
            createModel("claude-3-sonnet", "Claude 3 Sonnet", "text", 200000, 4096),
            createModel("claude-3-haiku", "Claude 3 Haiku", "text", 200000, 4096),
            createModel("claude-2.1", "Claude 2.1", "text", 200000, 4096),
            createModel("claude-2", "Claude 2", "text", 100000, 4096),
            createModel("gemini-pro", "Gemini Pro", "text", 32768, 8192),
            createModel("gemini-pro-vision", "Gemini Pro Vision", "text", 32768, 8192),
            createModel("moonshot-v1-8k", "Moonshot V1 8K", "text", 8192, 4096),
            createModel("moonshot-v1-32k", "Moonshot V1 32K", "text", 32768, 4096),
            createModel("moonshot-v1-128k", "Moonshot V1 128K", "text", 131072, 4096),
            createModel("deepseek-chat", "DeepSeek Chat", "text", 16384, 4096),
            createModel("deepseek-coder", "DeepSeek Coder", "text", 16384, 4096),
            createModel("Qwen/Qwen2-72B-Instruct", "Qwen2-72B", "text", 32768, 4096),
            createModel("minimax/MiniMax-Text-01", "MiniMax Text", "text", 245760, 4096)
    );

    /**
     * 获取模型列表。
     * GET /v1/models
     */
    @GetMapping("/models")
    public Map<String, Object> listModels() {
        TokenAuthContext context = TokenAuthContext.get();
        String tokenKey = context.getTokenKey();
        Long tokenId = context.getTokenId();

        log.info("[OpenAI兼容] 获取模型列表: maskedKey={}, tokenId={}",
                context.getMaskedTokenKey(), tokenId);

        List<Map<String, Object>> models;
        if (context.isModelLimitsEnabled() && context.getModelLimits() != null) {
            // 只返回允许的模型
            models = new ArrayList<>();
            for (Map<String, Object> model : SUPPORTED_MODELS) {
                String modelId = (String) model.get("id");
                if (context.isModelAllowed(modelId)) {
                    models.add(model);
                }
            }
        } else {
            models = SUPPORTED_MODELS;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("object", "list");
        response.put("data", models);

        return response;
    }

    /**
     * 获取指定模型信息。
     * GET /v1/models/{model}
     */
    @GetMapping("/models/{model}")
    public Map<String, Object> getModel(@PathVariable String model) {
        TokenAuthContext context = TokenAuthContext.get();

        log.info("[OpenAI兼容] 获取模型信息: model={}, maskedKey={}", model, context.getMaskedTokenKey());

        // 检查模型是否允许
        if (context.isModelLimitsEnabled() && !context.isModelAllowed(model)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", Map.of(
                    "message", "模型 '" + model + "' 不可用或未授权",
                    "type", "invalid_request_error",
                    "code", "model_not_found"
            ));
            return error;
        }

        // 查找模型
        for (Map<String, Object> m : SUPPORTED_MODELS) {
            if (m.get("id").equals(model)) {
                return m;
            }
        }

        Map<String, Object> error = new HashMap<>();
        error.put("error", Map.of(
                "message", "模型 '" + model + "' 不存在",
                "type", "invalid_request_error",
                "code", "model_not_found"
        ));
        return error;
    }

    /**
     * 创建模型辅助信息。
     */
    private static Map<String, Object> createModel(String id, String name, String type, int contextWindow, int outputLimit) {
        Map<String, Object> model = new HashMap<>();
        model.put("id", id);
        model.put("object", "model");
        model.put("created", 1677610602);
        model.put("owned_by", "system");
        model.put("permission", List.of());
        model.put("root", id);

        Map<String, Object> meta = new HashMap<>();
        meta.put("qualification", "qualified");

        Map<String, Object> details = new HashMap<>();
        details.put("mode", "chat");
        details.put("type", type);
        details.put("context_window", contextWindow);
        details.put("output_limit", outputLimit);
        details.put("enabled", true);

        model.put("meta", meta);
        model.put("mmcove_details", details);

        return model;
    }
}
