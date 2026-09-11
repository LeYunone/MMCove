package com.mmcove.agent.core.agent;

import com.mmcove.agent.common.model.entity.AgentDefinition;
import com.mmcove.agent.llm.gateway.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 智能路由器，通过关键词匹配 + LLM 意图识别自动匹配 Agent。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouter {

    private final AgentRegistry agentRegistry;
    private final LlmGateway llmGateway;

    /**
     * 检测用户消息应使用的 Agent。
     *
     * @param userMessage    用户消息
     * @param currentAgentId 会话当前已绑定的 Agent ID（可为 null）
     * @return 匹配到的 Agent 定义
     */
    public AgentDefinition detectAgent(String userMessage, String currentAgentId) {
        // 「精准防飘移」改造:不再「会话首问锁定 Agent」——每轮都重新判定意图。
        // 原因:会话锁定会导致话题切换后仍沿用旧 Agent(旧工具组),引发「答非所问/调错工具」;
        // 且与「每轮按意图收敛工具」的设计冲突。
        // currentAgentId 参数保留(供日志/未来扩展),不再用于锁定。
        // 路由顺序:关键词命中 → LLM 意图识别 → 默认 Agent。

        // 1. 获取所有活跃 Agent
        List<AgentDefinition> agents = agentRegistry.getAllAgents();
        if (agents.isEmpty()) {
            throw new IllegalStateException("未配置任何 Agent");
        }

        // 2. 如果只有一个 Agent，直接返回
        if (agents.size() == 1) {
            return agents.get(0);
        }

        // 3. 先尝试基于 description 关键词快速匹配(命中即返回,不调 LLM,控制成本)
        AgentDefinition keywordMatch = matchByKeyword(userMessage, agents);
        if (keywordMatch != null) {
            log.info("关键词匹配到 Agent: {} - {}", keywordMatch.getAgentId(), keywordMatch.getName());
            return keywordMatch;
        }

        // 4. 关键词未命中(或并列)，调用 LLM 意图识别
        String candidates = agents.stream()
                .map(a -> String.format("- %s: %s", a.getAgentId(), a.getDescription()))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "以下是可用的 AI 助手列表：\n%s\n\n用户消息：%s\n\n请从上面的列表中选择最合适处理这条消息的助手，只返回对应的 ID（如 general-assistant），不要返回任何其他内容。",
                candidates, userMessage
        );

        String detectedId = llmGateway.detectIntent(prompt);
        if (detectedId != null) {
            detectedId = detectedId.trim();
            AgentDefinition detected = agentRegistry.getAgent(detectedId);
            if (detected != null) {
                log.info("意图识别匹配到 Agent: {} - {}", detected.getAgentId(), detected.getName());
                return detected;
            }
            log.warn("意图识别返回了无效的 Agent ID: {}, 使用默认 Agent", detectedId);
        }

        // 5. 识别失败，返回默认 Agent
        AgentDefinition defaultAgent = agentRegistry.getDefaultAgent();
        if (defaultAgent == null) {
            throw new IllegalStateException("未配置默认 Agent");
        }
        log.info("意图识别失败，使用默认 Agent: {}", defaultAgent.getAgentId());
        return defaultAgent;
    }

    /**
     * 基于 Agent 的 description 做关键词匹配。
     * 将 description 拆分为关键词，检查用户消息是否命中。
     * 只有当一个 Agent 的关键词命中数明显多于其他 Agent 时才返回匹配结果。
     */
    private AgentDefinition matchByKeyword(String userMessage, List<AgentDefinition> agents) {
        String msg = userMessage.toLowerCase();
        AgentDefinition bestMatch = null;
        int bestScore = 0;
        boolean tied = false;

        for (AgentDefinition agent : agents) {
            int score = computeKeywordScore(msg, agent);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = agent;
                tied = false;
            } else if (score == bestScore && score > 0) {
                tied = true;
            }
        }

        // 至少命中 1 个关键词且没有并列，才返回
        if (bestScore > 0 && !tied) {
            return bestMatch;
        }
        return null;
    }

    /**
     * 计算 Agent description 中的关键词在用户消息中的命中数。
     */
    private int computeKeywordScore(String msg, AgentDefinition agent) {
        String desc = agent.getDescription();
        if (desc == null || desc.isBlank()) {
            return 0;
        }

        int score = 0;
        String name = agent.getName();
        if (name != null && msg.contains(name.toLowerCase())) {
            score += 3;
        }

        String[] keywords = desc.split("[，,、\\s]+");
        for (String kw : keywords) {
            if (kw.length() >= 2 && msg.contains(kw.toLowerCase())) {
                score += 1;
            }
        }
        return score;
    }

    /**
     * L2 top-k 候选(评审 ISSUE-004):关键词打分返回 top-k 候选 Agent,防硬收敛单点故障。
     * <p>分数 > 0 的前 k 个(降序);全 0 返回空(由 detectAgent 走 LLM 定主)。
     * 候选的 toolGroup 会与主 Agent 的 toolGroup 合并装载,确保路由边缘时正确工具不被物理移除。
     */
    public List<AgentDefinition> detectAgentCandidates(String userMessage, int k) {
        List<AgentDefinition> agents = agentRegistry.getAllAgents();
        if (agents.isEmpty() || k <= 0) {
            return List.of();
        }
        String msg = userMessage.toLowerCase();
        return agents.stream()
                .map(a -> new java.util.AbstractMap.SimpleEntry<>(a, computeKeywordScore(msg, a)))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(java.util.Map.Entry::getKey)
                .toList();
    }
}
