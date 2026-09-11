package com.mmcove.agent.core.prompt;

import com.mmcove.agent.common.model.entity.ResponseTemplate;
import com.mmcove.agent.core.template.ResponseTemplateRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统提示词构建器，用于构造 LLM 系统消息。
 * 支持将响应格式模板拼接到 Agent 的 systemPrompt 末尾。
 */
@Component
@RequiredArgsConstructor
public class SystemPromptBuilder {

    private final ResponseTemplateRegistry templateRegistry;

    private static final String RETRY_RULE = """

            ===== 重要行为规则 =====
            当用户对同一问题再次提问时，即使历史对话中已经有过失败或错误的回答，
            你也必须重新调用工具来获取最新结果，绝不能直接复用历史中的失败结论。
            用户可能已经更换了凭证或修复了问题，你应该给予重新尝试的机会。
            """;

    /** 「精准防飘移」L1 能力边界:所有 Agent 统一施加——超出能力范围拒答,不强行调用无关工具凑答案。 */
    private static final String CAPABILITY_BOUNDARY = """

            ===== 能力边界(重要) =====
            对于明显超出你能力范围的问题(如与本域无关的闲聊/天气/新闻/医疗/法律等),
            必须直接礼貌告知用户你无法处理,绝不能强行调用无关工具来"凑"一个答案。
            判断不准时,宁可拒答,也不要调用看起来无关的工具。
            """;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个由 Mmcove AI Agent 驱动的智能 AI 助手。
            你可以帮助用户完成各种任务并回答问题。
            当需要使用工具来完成任务时，系统会为你提供可用工具。
            请始终保持回复的有用性、准确性和简洁性。
            """;

    public String build() {
        return DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * 「精准防飘移」L1 能力边界 + 重试规则:供非 AgentOrchestrator 链路(如 ChatCompletionsController
     * 经 roleMessageService)拼接到 systemText,与 AgentOrchestrator 链路保持一致的能力边界约束。
     */
    public String getCapabilityBoundary() {
        return CAPABILITY_BOUNDARY + RETRY_RULE;
    }

    public String build(String customPrompt) {
        if (customPrompt == null || customPrompt.isBlank()) {
            return build() + CAPABILITY_BOUNDARY + RETRY_RULE;
        }
        return customPrompt + CAPABILITY_BOUNDARY + RETRY_RULE;
    }

    /**
     * 构建带响应格式模板的系统提示词。
     *
     * @param customPrompt Agent 原始 systemPrompt
     * @param agentId      Agent ID，用于匹配绑定的模板
     * @return 拼接模板后的完整 systemPrompt
     */
    public String build(String customPrompt, String agentId) {
        String basePrompt = build(customPrompt);
        String templateSection = buildTemplateSection(agentId);
        if (templateSection == null || templateSection.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n" + templateSection;
    }

    /**
     * 构建模板拼接段落。
     */
    private String buildTemplateSection(String agentId) {
        List<ResponseTemplate> toolTemplates = templateRegistry.getTemplates("TOOL", agentId);
        List<ResponseTemplate> sceneTemplates = templateRegistry.getTemplates("SCENE", agentId);

        if (toolTemplates.isEmpty() && sceneTemplates.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("===== 响应格式规范 =====\n");
        sb.append("请严格遵守以下输出格式规范来组织你的回答：\n");

        if (!toolTemplates.isEmpty()) {
            sb.append("\n--- 工具结果格式 ---\n");
            for (ResponseTemplate t : toolTemplates) {
                sb.append(t.getTemplateContent()).append("\n");
            }
        }

        if (!sceneTemplates.isEmpty()) {
            sb.append("\n--- 通用场景格式 ---\n");
            for (ResponseTemplate t : sceneTemplates) {
                sb.append(t.getTemplateContent()).append("\n");
            }
        }

        return sb.toString();
    }
}
