package com.mmcove.agent.core.thinking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.model.dto.TaskPlanDto;
import com.mmcove.agent.llm.gateway.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务规划器：调用 LLM 判断是否需要拆分任务。
 * <p>
 * 使用独立的 LLM chat 调用（不使用 detectIntent），确保 system prompt 正确注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPlanner {

    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    private static final String PLANNING_SYSTEM_PROMPT = """
            你是一个任务规划助手。分析用户的消息，判断是否需要拆分为多个子任务。

            规则：
            1. 如果用户的消息只涉及一个简单任务或不需要调用工具，返回 {"needSplit": false}
            2. 如果涉及多个独立的信息查询或操作（如同时查询用户信息和机构信息），返回拆分计划
            3. 如果一个任务的输出是另一个任务的输入，标记为串行（parallel=false）
            4. 如果多个任务之间无依赖关系，标记为并行（parallel=true）

            返回严格的 JSON 格式，不要包含其他内容：
            {"needSplit": true/false, "tasks": [{"index": 0, "title": "任务描述", "parallel": true/false}]}
            """;

    /**
     * 规划任务拆分。
     *
     * @param userMessage 用户消息
     * @return 任务计划
     */
    public TaskPlanDto plan(String userMessage) {
        try {
            String prompt = "用户消息：" + userMessage;

            // 使用 callWithoutTools() — 不注入工具回调，避免 LLM API 400 错误
            String result = llmGateway.callWithoutTools(PLANNING_SYSTEM_PROMPT, prompt);

            if (result == null || result.isBlank()) {
                return buildSinglePlan();
            }

            // 提取 JSON
            String json = extractJson(result);
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});

            boolean needSplit = Boolean.TRUE.equals(parsed.get("needSplit"));
            if (!needSplit) {
                return buildSinglePlan();
            }

            Object rawTasks = parsed.get("tasks");
            if (!(rawTasks instanceof List)) {
                return buildSinglePlan();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> taskMaps = (List<Map<String, Object>>) rawTasks;
            if (taskMaps.isEmpty()) {
                return buildSinglePlan();
            }

            List<TaskPlanDto.TaskItem> tasks = new ArrayList<>();
            for (int i = 0; i < taskMaps.size(); i++) {
                Map<String, Object> taskMap = taskMaps.get(i);
                // 防御：LLM 可能不返回 index，使用列表序号兜底
                int index = taskMap.get("index") instanceof Number n
                        ? n.intValue()
                        : i;
                String title = taskMap.get("title") instanceof String s
                        ? s
                        : "子任务" + index;
                boolean parallel = Boolean.TRUE.equals(taskMap.get("parallel"));

                tasks.add(TaskPlanDto.TaskItem.builder()
                        .index(index)
                        .title(title)
                        .parallel(parallel)
                        .build());
            }

            return TaskPlanDto.builder()
                    .planId("plan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .needSplit(true)
                    .tasks(tasks)
                    .build();

        } catch (Exception e) {
            log.warn("任务规划失败，降级为单任务: {}", e.getMessage());
            return buildSinglePlan();
        }
    }

    private TaskPlanDto buildSinglePlan() {
        return TaskPlanDto.builder()
                .needSplit(false)
                .build();
    }

    /**
     * 从 LLM 响应中提取 JSON（可能被 markdown 代码块包裹）。
     */
    private String extractJson(String text) {
        text = text.trim();
        // 去除 markdown 代码块
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        // 提取 JSON 对象
        int jsonStart = text.indexOf('{');
        int jsonEnd = text.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1);
        }
        return text;
    }
}
