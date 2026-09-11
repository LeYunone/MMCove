package com.mmcove.agent.core.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmcove.agent.common.enums.ChatResponseType;
import com.mmcove.agent.common.enums.MessageRole;
import com.mmcove.agent.common.enums.SseEventType;
import com.mmcove.agent.common.enums.SubTaskStatus;
import com.mmcove.agent.common.exception.LlmCallException;
import com.mmcove.agent.common.model.dto.ChatResponse;
import com.mmcove.agent.common.model.dto.TaskPlanDto;
import com.mmcove.agent.common.model.entity.AgentDefinition;
import com.mmcove.agent.common.model.entity.Conversation;
import com.mmcove.agent.common.model.entity.ConversationMessage;
import com.mmcove.agent.common.model.entity.SubTask;
import com.mmcove.agent.core.agent.AgentRouter;
import com.mmcove.agent.core.dialog.DialogManager;
import com.mmcove.agent.core.dialog.EntityMemoryService;
import com.mmcove.agent.core.prompt.SystemPromptBuilder;
import com.mmcove.agent.core.thinking.TaskPlanner;
import com.mmcove.agent.core.thinking.ThinkingChainRecorder;
import com.mmcove.agent.infra.persistence.repository.GroupToolConfigRepository;
import com.mmcove.agent.infra.persistence.repository.SubTaskRepository;
import com.mmcove.agent.infra.sse.SseEventStore;
import com.mmcove.agent.llm.callback.ObservableToolCallback;
import com.mmcove.agent.common.context.KnowledgeContextHolder;
import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.llm.gateway.LlmGateway;
import com.mmcove.agent.rag.routing.KnowledgeBaseRouter;
import org.springframework.beans.factory.ObjectProvider;
import com.mmcove.agent.llm.memory.ConversationContext;
import com.mmcove.agent.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * ReAct 编排器 - Agent 系统的核心入口。
 * 支持思维链事件、任务拆分和 SSE 事件持久化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    /** 全局工具组:挂在这里的工具对所有 Agent 可见(生图/时间等跨域能力),不随意图路由收敛。 */
    private static final String COMMON_TOOL_GROUP = "common";

    private final LlmGateway llmGateway;
    private final DialogManager dialogManager;
    private final AgentRouter agentRouter;
    private final SystemPromptBuilder systemPromptBuilder;
    private final TaskPlanner taskPlanner;
    private final SseEventStore sseEventStore;
    private final ToolRegistry toolRegistry;
    private final GroupToolConfigRepository groupToolConfigRepository;
    private final SubTaskRepository subTaskRepository;
    private final ThinkingChainRecorder thinkingChainRecorder;
    private final EntityMemoryService entityMemoryService;
    private final DriftCircuitBreaker driftCircuitBreaker;
    /** 知识库场景选路(mmcove-rag 可选注入;无 mmcove-rag 时为空,自动跳过,零侵入) */
    private final ObjectProvider<KnowledgeBaseRouter> knowledgeBaseRouterProvider;

    /**
     * 知识库场景选库钩子:mmcove-rag 不在 classpath 时自动跳过(零侵入)。
     * 选中的 kbId 写入 KnowledgeContextHolder,供 searchKnowledge 工具读取;
     * 先 clear 防线程复用残留。
     */
    private void routeKnowledgeBaseIfPresent(String userMessage, String agentId) {
        KnowledgeContextHolder.clear();
        knowledgeBaseRouterProvider.ifAvailable(router -> {
            try {
                KnowledgeBase kb = router.route(userMessage, agentId);
                if (kb != null) {
                    KnowledgeContextHolder.set(kb.getId());
                }
            } catch (Exception e) {
                log.warn("知识库选库异常,跳过: {}", e.getMessage());
                KnowledgeContextHolder.clear();
            }
        });
    }

    /**
     * 编排同步聊天交互（带分组工具）。
     */
    public ChatResponse orchestrate(String sessionId, String userMessage, String groupName) {
        // 失败兜底:同步路径也生成 correlation ID,接入 wrapToolCallbacksByGroups(emitter=null)
        String correlationId = generateCorrelationId();
        log.info("开始编排聊天，会话: {}, 分组: {} [cid={}]", sessionId, groupName, correlationId);

        Conversation existingConversation = dialogManager.getSession(sessionId);
        String currentAgentId = existingConversation != null ? existingConversation.getAgentType() : null;

        AgentDefinition agent = agentRouter.detectAgent(userMessage, currentAgentId);
        routeKnowledgeBaseIfPresent(userMessage, agent.getAgentId());

        if (existingConversation != null
                && (existingConversation.getAgentType() == null || existingConversation.getAgentType().isBlank())) {
            existingConversation.setAgentType(agent.getAgentId());
            dialogManager.updateConversation(existingConversation);
        }

        ConversationContext context = dialogManager.loadContext(sessionId);
        context.setSystemPrompt(systemPromptBuilder.build(agent.getSystemPrompt(), agent.getAgentId()));
        context.setAgentId(agent.getAgentId());

        injectRetryHintIfNeeded(sessionId, context);
        dialogManager.appendMessage(sessionId, MessageRole.USER, userMessage);

        try {
            // 失败兜底:同步路径也接入 ObservableToolCallback(cid/慢调用/思考链/实体记忆/熔断信号),emitter=null 跳过 SSE
            List<ToolCallback> tools = wrapToolCallbacksByGroups(resolveToolGroups(agent, userMessage), groupName, null, sessionId, null, correlationId);
            ChatResponse response = llmGateway.chatWithTools(userMessage, context, tools);

            if (response.getContent() != null) {
                dialogManager.appendMessage(sessionId, MessageRole.ASSISTANT, response.getContent());
            }

            dialogManager.saveContext(sessionId, context);
            return response;

        } catch (LlmCallException e) {
            log.error("会话 {} 编排失败: {}", sessionId, e.getMessage());
            return ChatResponse.builder()
                    .type(ChatResponseType.ERROR)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * 编排同步聊天交互。
     */
    public ChatResponse orchestrate(String sessionId, String userMessage) {
        Conversation existingConversation = dialogManager.getSession(sessionId);
        String currentAgentId = existingConversation != null ? existingConversation.getAgentType() : null;

        AgentDefinition agent = agentRouter.detectAgent(userMessage, currentAgentId);
        routeKnowledgeBaseIfPresent(userMessage, agent.getAgentId());

        if (existingConversation != null
                && (existingConversation.getAgentType() == null || existingConversation.getAgentType().isBlank())) {
            existingConversation.setAgentType(agent.getAgentId());
            dialogManager.updateConversation(existingConversation);
        }

        ConversationContext context = dialogManager.loadContext(sessionId);
        context.setSystemPrompt(systemPromptBuilder.build(agent.getSystemPrompt(), agent.getAgentId()));
        context.setAgentId(agent.getAgentId());

        injectRetryHintIfNeeded(sessionId, context);
        dialogManager.appendMessage(sessionId, MessageRole.USER, userMessage);

        try {
            ChatResponse response = llmGateway.chat(userMessage, context);

            if (response.getContent() != null) {
                dialogManager.appendMessage(sessionId, MessageRole.ASSISTANT, response.getContent());
            }

            dialogManager.saveContext(sessionId, context);
            return response;

        } catch (LlmCallException e) {
            log.error("会话 {} 编排失败: {}", sessionId, e.getMessage());
            return ChatResponse.builder()
                    .type(ChatResponseType.ERROR)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * 增强流式编排（带分组工具）。
     */
    public Flux<ChatResponse> orchestrateStream(String sessionId, String userMessage, SseEmitter emitter, String groupName) {
        // 失败兜底:每请求生成 correlation ID,贯穿日志/SSE/思考链,故障可串联定位
        String correlationId = generateCorrelationId();
        log.info("开始编排增强流式聊天，会话: {}, 分组: {} [cid={}]", sessionId, groupName, correlationId);

        return Flux.defer(() -> {
            Conversation existingConversation = dialogManager.getSession(sessionId);
            String currentAgentId = existingConversation != null ? existingConversation.getAgentType() : null;

            AgentDefinition agent = agentRouter.detectAgent(userMessage, currentAgentId);
            routeKnowledgeBaseIfPresent(userMessage, agent.getAgentId());
        routeKnowledgeBaseIfPresent(userMessage, agent.getAgentId());

            if (existingConversation != null
                    && (existingConversation.getAgentType() == null || existingConversation.getAgentType().isBlank())) {
                existingConversation.setAgentType(agent.getAgentId());
                dialogManager.updateConversation(existingConversation);
            }

            ConversationContext context = dialogManager.loadContext(sessionId);
            context.setSystemPrompt(systemPromptBuilder.build(agent.getSystemPrompt(), agent.getAgentId()));
            context.setAgentId(agent.getAgentId());

            injectRetryHintIfNeeded(sessionId, context);
            dialogManager.appendMessage(sessionId, MessageRole.USER, userMessage);

            Map<String, Object> thinkingPayload = new LinkedHashMap<>();
            thinkingPayload.put("content", "正在分析您的问题...");
            thinkingPayload.put("phase", "planning");
            sseEventStore.emitEvent(emitter, sessionId, SseEventType.THINKING, null, thinkingPayload);

            TaskPlanDto plan;
            try {
                plan = taskPlanner.plan(userMessage);
            } catch (Exception e) {
                log.warn("任务规划异常，降级为单任务: {}", e.getMessage());
                plan = TaskPlanDto.builder().needSplit(false).build();
            }

            if (!plan.isNeedSplit()) {
                return executeSingleTaskByGroup(sessionId, userMessage, context, emitter, groupName, resolveToolGroups(agent, userMessage), correlationId);
            } else {
                return executeMultiTaskByGroup(sessionId, userMessage, context, emitter, plan, groupName, resolveToolGroups(agent, userMessage), correlationId);
            }
        });
    }

    /**
     * 增强流式编排（含思维链事件 + 任务拆分 + SSE 持久化）。
     * <p>
     * 关键：使用 Flux.defer() 确保所有逻辑在 subscribe 后执行，
     * 而非在构建 Flux 时就执行（否则时序错乱）。
     */
    public Flux<ChatResponse> orchestrateStream(String sessionId, String userMessage, SseEmitter emitter) {
        log.info("开始编排增强流式聊天，会话: {}", sessionId);

        return Flux.defer(() -> {
            // 1. 路由 + 加载上下文
            Conversation existingConversation = dialogManager.getSession(sessionId);
            String currentAgentId = existingConversation != null ? existingConversation.getAgentType() : null;

            AgentDefinition agent = agentRouter.detectAgent(userMessage, currentAgentId);
            routeKnowledgeBaseIfPresent(userMessage, agent.getAgentId());
        routeKnowledgeBaseIfPresent(userMessage, agent.getAgentId());

            if (existingConversation != null
                    && (existingConversation.getAgentType() == null || existingConversation.getAgentType().isBlank())) {
                existingConversation.setAgentType(agent.getAgentId());
                dialogManager.updateConversation(existingConversation);
            }

            ConversationContext context = dialogManager.loadContext(sessionId);
            context.setSystemPrompt(systemPromptBuilder.build(agent.getSystemPrompt(), agent.getAgentId()));
            context.setAgentId(agent.getAgentId());

            injectRetryHintIfNeeded(sessionId, context);
            dialogManager.appendMessage(sessionId, MessageRole.USER, userMessage);

            // 2. 发出 thinking(planning) 事件
            Map<String, Object> thinkingPayload = new LinkedHashMap<>();
            thinkingPayload.put("content", "正在分析您的问题...");
            thinkingPayload.put("phase", "planning");
            sseEventStore.emitEvent(emitter, sessionId, SseEventType.THINKING, null, thinkingPayload);

            // 3. 任务规划
            TaskPlanDto plan;
            try {
                plan = taskPlanner.plan(userMessage);
            } catch (Exception e) {
                log.warn("任务规划异常，降级为单任务: {}", e.getMessage());
                plan = TaskPlanDto.builder().needSplit(false).build();
            }

            // 4. 根据规划执行
            if (!plan.isNeedSplit()) {
                return executeSingleTask(sessionId, userMessage, context, emitter);
            } else {
                return executeMultiTask(sessionId, userMessage, context, emitter, plan);
            }
        });
    }

    /**
     * 原始流式编排（向后兼容，无 SSE 增强）。
     */
    public Flux<ChatResponse> orchestrateStream(String sessionId, String userMessage) {
        log.info("开始编排流式聊天，会话: {}", sessionId);

        Conversation existingConversation = dialogManager.getSession(sessionId);
        String currentAgentId = existingConversation != null ? existingConversation.getAgentType() : null;

        AgentDefinition agent = agentRouter.detectAgent(userMessage, currentAgentId);
        routeKnowledgeBaseIfPresent(userMessage, agent.getAgentId());

        if (existingConversation != null
                && (existingConversation.getAgentType() == null || existingConversation.getAgentType().isBlank())) {
            existingConversation.setAgentType(agent.getAgentId());
            dialogManager.updateConversation(existingConversation);
        }

        ConversationContext context = dialogManager.loadContext(sessionId);
        context.setSystemPrompt(systemPromptBuilder.build(agent.getSystemPrompt(), agent.getAgentId()));
        context.setAgentId(agent.getAgentId());

        injectRetryHintIfNeeded(sessionId, context);
        dialogManager.appendMessage(sessionId, MessageRole.USER, userMessage);

        StringBuilder fullResponse = new StringBuilder();
        return llmGateway.chatStream(userMessage, context)
                .doOnNext(chunk -> {
                    if (chunk.getContent() != null) {
                        fullResponse.append(chunk.getContent());
                    }
                })
                .doOnComplete(() -> {
                    if (!fullResponse.isEmpty()) {
                        dialogManager.appendMessage(sessionId, MessageRole.ASSISTANT, fullResponse.toString());
                    }
                    dialogManager.saveContext(sessionId, context);
                })
                .doOnError(e -> {
                    log.error("会话 {} 流式编排失败: {}", sessionId, e.getMessage());
                });
    }

    /**
     * 执行单任务（不拆分）。
     */
    private Flux<ChatResponse> executeSingleTask(String sessionId, String userMessage,
                                                  ConversationContext context, SseEmitter emitter) {
        // 发出 thinking(analyzing) 事件
        Map<String, Object> analyzingPayload = new LinkedHashMap<>();
        analyzingPayload.put("content", "正在分析并处理您的问题...");
        analyzingPayload.put("phase", "analyzing");
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.THINKING, null, analyzingPayload);

        // 使用 ObservableToolCallback 包装工具（注入 thinkingChainRecorder）
        List<ToolCallback> observableCallbacks = wrapToolCallbacks(emitter, sessionId, null);

        // 发出 final_answer 事件
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.FINAL_ANSWER,
                null, Map.of("content", ""));

        StringBuilder fullResponse = new StringBuilder();
        return llmGateway.chatStreamWithTools(userMessage, context, observableCallbacks)
                .doOnNext(chunk -> {
                    if (chunk.getContent() != null) {
                        fullResponse.append(chunk.getContent());
                    }
                })
                .doOnComplete(() -> {
                    if (!fullResponse.isEmpty()) {
                        dialogManager.appendMessage(sessionId, MessageRole.ASSISTANT, fullResponse.toString());
                    }
                    dialogManager.saveContext(sessionId, context);
                })
                .doOnError(e -> {
                    log.error("单任务流式执行失败: {}", e.getMessage());
                });
    }

    /**
     * 执行多任务（拆分后）。
     * <p>
     * 子任务同步执行（LLM 调用本身是同步的，串行执行保证 SSE 事件有序），
     * 结果汇总阶段使用 Flux 流式输出。
     */
    private Flux<ChatResponse> executeMultiTask(String sessionId, String userMessage,
                                                 ConversationContext context, SseEmitter emitter,
                                                 TaskPlanDto plan) {
        // 发出 task_plan 事件
        Map<String, Object> planPayload = new LinkedHashMap<>();
        planPayload.put("planId", plan.getPlanId());
        planPayload.put("tasks", plan.getTasks().stream().map(t -> {
            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("index", t.getIndex());
            taskMap.put("title", t.getTitle());
            taskMap.put("parallel", t.isParallel());
            return taskMap;
        }).collect(Collectors.toList()));
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_PLAN, null, planPayload);

        // 存储子任务结果（线程安全，为后续并行执行预留）
        Map<Integer, String> taskResults = new ConcurrentHashMap<>();

        // 保存子任务到数据库
        for (TaskPlanDto.TaskItem item : plan.getTasks()) {
            SubTask subTask = new SubTask();
            subTask.setSessionId(sessionId);
            subTask.setTaskPlanId(plan.getPlanId());
            subTask.setTaskIndex(item.getIndex());
            subTask.setTitle(item.getTitle());
            subTask.setStatus(SubTaskStatus.PENDING.name());
            subTask.setParallel(item.isParallel());
            subTaskRepository.save(subTask);
        }

        // 串行执行所有子任务（保证 SSE 事件有序，并行模式待后续用 CompletableFuture 优化）
        for (TaskPlanDto.TaskItem taskItem : plan.getTasks()) {
            String subTaskId = "sub_" + taskItem.getIndex();
            executeSubTask(sessionId, userMessage, context, emitter, taskItem, subTaskId, taskResults);
        }

        // 所有子任务完成后，LLM 汇总结果
        String summaryPrompt = buildSummaryPrompt(userMessage, plan, taskResults);

        // 发出 final_answer 事件
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.FINAL_ANSWER,
                null, Map.of("content", ""));

        ConversationContext summaryContext = dialogManager.loadContext(sessionId);
        summaryContext.setSystemPrompt(context.getSystemPrompt());
        summaryContext.setAgentId(context.getAgentId());

        StringBuilder fullResponse = new StringBuilder();
        return llmGateway.chatStream(summaryPrompt, summaryContext)
                .doOnNext(chunk -> {
                    if (chunk.getContent() != null) {
                        fullResponse.append(chunk.getContent());
                    }
                })
                .doOnComplete(() -> {
                    if (!fullResponse.isEmpty()) {
                        dialogManager.appendMessage(sessionId, MessageRole.ASSISTANT, fullResponse.toString());
                    }
                    dialogManager.saveContext(sessionId, context);
                })
                .doOnError(e -> {
                    log.error("多任务汇总失败: {}", e.getMessage());
                });
    }

    /**
     * 执行单个子任务。
     */
    private void executeSubTask(String sessionId, String userMessage,
                                ConversationContext context, SseEmitter emitter,
                                TaskPlanDto.TaskItem taskItem, String subTaskId,
                                Map<Integer, String> taskResults) {
        // 发出 task_start 事件
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_START,
                subTaskId, Map.of("taskIndex", taskItem.getIndex(), "title", taskItem.getTitle()));

        // 精确更新状态
        subTaskRepository.updateStatusBySessionAndIndex(sessionId, taskItem.getIndex(), SubTaskStatus.RUNNING.name());

        try {
            List<ToolCallback> observableCallbacks = wrapToolCallbacks(emitter, sessionId, subTaskId);

            String subTaskPrompt = buildSubTaskPrompt(userMessage, taskItem, taskResults);
            ChatResponse response = llmGateway.chatWithTools(subTaskPrompt, context, observableCallbacks);
            String result = response.getContent() != null ? response.getContent() : "";

            taskResults.put(taskItem.getIndex(), result);

            // 发出 task_complete 事件（只包含状态，不包含完整结果，结果供汇总使用）
            sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_COMPLETE,
                    subTaskId, Map.of(
                            "taskIndex", taskItem.getIndex(),
                            "title", taskItem.getTitle(),
                            "status", "completed"
                    ));

            subTaskRepository.updateResultBySessionAndIndex(sessionId, taskItem.getIndex(),
                    SubTaskStatus.COMPLETED.name(), result);

        } catch (Exception e) {
            log.error("子任务 {} 执行失败: {}", taskItem.getIndex(), e.getMessage());

            sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_COMPLETE,
                    subTaskId, Map.of(
                            "taskIndex", taskItem.getIndex(),
                            "title", taskItem.getTitle(),
                            "status", "failed"
                    ));

            subTaskRepository.updateResultBySessionAndIndex(sessionId, taskItem.getIndex(),
                    SubTaskStatus.FAILED.name(), null);
        }
    }

    /**
     * 使用分组工具执行单任务。
     */
    private Flux<ChatResponse> executeSingleTaskByGroup(String sessionId, String userMessage,
                                                         ConversationContext context, SseEmitter emitter,
                                                         String channelGroup, Set<String> toolGroups, String correlationId) {
        Map<String, Object> analyzingPayload = new LinkedHashMap<>();
        analyzingPayload.put("content", "正在分析并处理您的问题...");
        analyzingPayload.put("phase", "analyzing");
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.THINKING, null, analyzingPayload);

        List<ToolCallback> observableCallbacks = wrapToolCallbacksByGroups(toolGroups, channelGroup, emitter, sessionId, null, correlationId);

        sseEventStore.emitEvent(emitter, sessionId, SseEventType.FINAL_ANSWER,
                null, Map.of("content", ""));

        StringBuilder fullResponse = new StringBuilder();
        return llmGateway.chatStreamWithTools(userMessage, context, observableCallbacks)
                .doOnNext(chunk -> {
                    if (chunk.getContent() != null) {
                        fullResponse.append(chunk.getContent());
                    }
                })
                .doOnComplete(() -> {
                    if (!fullResponse.isEmpty()) {
                        dialogManager.appendMessage(sessionId, MessageRole.ASSISTANT, fullResponse.toString());
                    }
                    dialogManager.saveContext(sessionId, context);
                })
                .doOnError(e -> log.error("分组单任务流式执行失败: {}", e.getMessage()));
    }

    /**
     * 使用分组工具执行多任务。
     */
    private Flux<ChatResponse> executeMultiTaskByGroup(String sessionId, String userMessage,
                                                        ConversationContext context, SseEmitter emitter,
                                                        TaskPlanDto plan, String channelGroup, Set<String> toolGroups, String correlationId) {
        Map<String, Object> planPayload = new LinkedHashMap<>();
        planPayload.put("planId", plan.getPlanId());
        planPayload.put("tasks", plan.getTasks().stream().map(t -> {
            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("index", t.getIndex());
            taskMap.put("title", t.getTitle());
            taskMap.put("parallel", t.isParallel());
            return taskMap;
        }).collect(Collectors.toList()));
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_PLAN, null, planPayload);

        Map<Integer, String> taskResults = new ConcurrentHashMap<>();

        for (TaskPlanDto.TaskItem item : plan.getTasks()) {
            SubTask subTask = new SubTask();
            subTask.setSessionId(sessionId);
            subTask.setTaskPlanId(plan.getPlanId());
            subTask.setTaskIndex(item.getIndex());
            subTask.setTitle(item.getTitle());
            subTask.setStatus(SubTaskStatus.PENDING.name());
            subTask.setParallel(item.isParallel());
            subTaskRepository.save(subTask);
        }

        for (TaskPlanDto.TaskItem taskItem : plan.getTasks()) {
            String subTaskId = "sub_" + taskItem.getIndex();
            List<ToolCallback> observableCallbacks = wrapToolCallbacksByGroups(toolGroups, channelGroup, emitter, sessionId, subTaskId, correlationId);

            try {
                String subTaskPrompt = buildSubTaskPrompt(userMessage, taskItem, taskResults);
                ChatResponse response = llmGateway.chatWithTools(subTaskPrompt, context, observableCallbacks);
                String result = response.getContent() != null ? response.getContent() : "";
                taskResults.put(taskItem.getIndex(), result);

                sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_COMPLETE,
                        subTaskId, Map.of("taskIndex", taskItem.getIndex(), "title", taskItem.getTitle(), "status", "completed"));
                subTaskRepository.updateResultBySessionAndIndex(sessionId, taskItem.getIndex(), SubTaskStatus.COMPLETED.name(), result);
            } catch (Exception e) {
                log.error("分组子任务 {} 执行失败: {}", taskItem.getIndex(), e.getMessage());
                sseEventStore.emitEvent(emitter, sessionId, SseEventType.TASK_COMPLETE,
                        subTaskId, Map.of("taskIndex", taskItem.getIndex(), "title", taskItem.getTitle(), "status", "failed"));
                subTaskRepository.updateResultBySessionAndIndex(sessionId, taskItem.getIndex(), SubTaskStatus.FAILED.name(), null);
            }
        }

        String summaryPrompt = buildSummaryPrompt(userMessage, plan, taskResults);
        sseEventStore.emitEvent(emitter, sessionId, SseEventType.FINAL_ANSWER, null, Map.of("content", ""));

        ConversationContext summaryContext = dialogManager.loadContext(sessionId);
        summaryContext.setSystemPrompt(context.getSystemPrompt());
        summaryContext.setAgentId(context.getAgentId());

        StringBuilder fullResponse = new StringBuilder();
        return llmGateway.chatStream(summaryPrompt, summaryContext)
                .doOnNext(chunk -> {
                    if (chunk.getContent() != null) {
                        fullResponse.append(chunk.getContent());
                    }
                })
                .doOnComplete(() -> {
                    if (!fullResponse.isEmpty()) {
                        dialogManager.appendMessage(sessionId, MessageRole.ASSISTANT, fullResponse.toString());
                    }
                    dialogManager.saveContext(sessionId, context);
                })
                .doOnError(e -> log.error("分组多任务汇总失败: {}", e.getMessage()));
    }

    /**
     * 获取分组对应的工具回调列表（公开接口，供 Controller 层调用）。
     */
    public List<ToolCallback> getGroupToolCallbacks(String groupName) {
        return wrapToolCallbacksByGroup(groupName);
    }

    /**
     * 获取分组对应的工具回调列表（带 SSE 包装，供流式接口调用）。
     * <p>工具调用过程中的事件（task_progress / image / confirmation 等）会通过 emitter
     * 实时推送给前端，供 chat/completions 等流式接口识别生图结果、确认请求等场景使用。
     *
     * @param groupName 分组名称
     * @param emitter   当前请求的 SSE emitter，用于推送工具事件
     * @param sessionId 会话 ID（用于事件持久化与追踪，可为临时 ID）
     * @return 已被 ObservableToolCallback 装饰的工具回调列表
     */
    public List<ToolCallback> getGroupToolCallbacks(String groupName, SseEmitter emitter, String sessionId) {
        return wrapToolCallbacksByGroup(groupName, groupName, emitter, sessionId, null);
    }

    /**
     * 按用户消息路由 + 收敛工具 + 接入失败兜底(供 ChatCompletionsController 等非编排链路调用)。
     * <p>封装 L2 意图路由(detectAgent)+ top-k 候选合并(resolveToolGroups)+ 工具收敛装载
     * (wrapToolCallbacksByGroups:含 cid 贯穿 / 熔断只读过滤 / onThinkingTurn 熔断信号 / Observable 包装)。
     * <p>channelGroup 来自 API Token(模型渠道路由),toolGroup 由 detectAgent 决定(工具收敛),两者分离。
     *
     * @param userMessage  用户当前提问(意图路由用,null/空时走默认 Agent 回退全量)
     * @param channelGroup 渠道分组(Token,传 ObservableToolCallback 做模型渠道路由)
     * @param emitter      SSE emitter(同步路径传 null,SseEventStore 已判空)
     * @param sessionId    会话 ID(可为临时 ID)
     */
    public List<ToolCallback> getRoutedToolCallbacks(String userMessage, String channelGroup,
                                                      SseEmitter emitter, String sessionId) {
        String correlationId = generateCorrelationId();
        String msg = userMessage == null ? "" : userMessage;
        AgentDefinition agent = agentRouter.detectAgent(msg, null);
        routeKnowledgeBaseIfPresent(msg, agent.getAgentId());
        Set<String> toolGroups = resolveToolGroups(agent, msg);
        log.info("[GroupTools] 路由收敛: agent={}, toolGroups={}, 渠道组={} [cid={}]",
                agent != null ? agent.getAgentId() : null, toolGroups, channelGroup, correlationId);
        return wrapToolCallbacksByGroups(toolGroups, channelGroup, emitter, sessionId, null, correlationId);
    }

    /**
     * 按工具组装载工具回调(无 SSE 包装,用于同步调用)。空组回退全量。
     * <p>「精准防飘移」:toolGroup 来自路由结果 agent.getToolGroupName()。
     */
    private List<ToolCallback> wrapToolCallbacksByGroup(String toolGroup) {
        return loadCallbacksByGroupName(toolGroup);
    }

    /**
     * 按工具组装载 + Observable 包装。
     * <p>「精准防飘移」改造:分离两个 group 语义——
     * <ul>
     *   <li>toolGroup:来自路由结果 {@code agent.getToolGroupName()},决定装载哪些工具(工具收敛)</li>
     *   <li>channelGroup:来自 API Token,传给 ObservableToolCallback 用于模型渠道路由(TokenAuthContext)</li>
     * </ul>
     * 空组(toolGroup 无配置)回退挂全量(未识别意图兜底,保证 LLM 有工具可选)。
     */
    private List<ToolCallback> wrapToolCallbacksByGroup(String toolGroup, String channelGroup,
                                                        SseEmitter emitter, String sessionId, String subTaskId) {
        List<ToolCallback> callbacks = loadCallbacksByGroupName(toolGroup);
        log.info("[GroupTools] 工具组「{}」加载 {} 个工具回调(渠道组:{})", toolGroup, callbacks.size(), channelGroup);
        AtomicInteger turnCounter = new AtomicInteger(0);
        return callbacks.stream()
                .map(cb -> (ToolCallback) new ObservableToolCallback(
                        cb, emitter, sessionId, subTaskId,
                        sseEventStore, thinkingChainRecorder::record, turnCounter, channelGroup,
                        this::rememberEntity))
                .collect(Collectors.toList());
    }

    /**
     * 按工具组名加载工具回调:查 group_tool_config,空配置回退挂全量。
     * 兜底语义:default 组或未配置组→全量,未识别意图时 LLM 仍有工具可选。
     */
    private List<ToolCallback> loadCallbacksByGroupName(String toolGroup) {
        Set<String> enabledToolNames = resolveGroupToolNames(toolGroup);
        if (enabledToolNames.isEmpty()) {
            log.info("[GroupTools] 工具组「{}」无启用配置,回退挂全量工具(未识别意图兜底)", toolGroup);
            return toolRegistry.getToolCallbacks();
        }
        // 合并全局工具(common 组),让单组装载也拥有跨域能力(生图/时间等)
        enabledToolNames.addAll(resolveGroupToolNames(COMMON_TOOL_GROUP));
        return toolRegistry.getToolCallbacksByNames(enabledToolNames);
    }

    /**
     * L2 top-k:合并多个工具组的工具(去重),空则回退全量(兜底)。
     */
    private List<ToolCallback> loadCallbacksByGroupNames(Set<String> toolGroups) {
        // 域工具组(随意图路由收敛)
        Set<String> domainToolNames = new HashSet<>();
        for (String g : toolGroups) {
            domainToolNames.addAll(resolveGroupToolNames(g));
        }
        // 域组为空(未识别意图/default)→ 回退全量(全量已含全局工具,无需再合并 common)
        if (domainToolNames.isEmpty()) {
            log.info("[GroupTools] 工具组集合{}无启用配置,回退挂全量工具", toolGroups);
            return toolRegistry.getToolCallbacks();
        }
        // 域工具 + 全局工具(common 组,对所有 Agent 可见)合并装载
        Set<String> allToolNames = new HashSet<>(domainToolNames);
        allToolNames.addAll(resolveGroupToolNames(COMMON_TOOL_GROUP));
        log.info("[GroupTools] 工具组集合{} + 全局组[{}] 合并加载 {} 个工具",
                toolGroups, COMMON_TOOL_GROUP, allToolNames.size());
        return toolRegistry.getToolCallbacksByNames(allToolNames);
    }

    /**
     * L2 top-k:主 Agent 的 toolGroup + 候选 Agent 的 toolGroups 合并(防硬收敛单点故障)。
     */
    private Set<String> resolveToolGroups(AgentDefinition agent, String userMessage) {
        Set<String> groups = new java.util.LinkedHashSet<>();
        String main = resolveToolGroupName(agent);
        if (main != null) {
            groups.add(main);
        }
        // top-k 候选(防路由边缘:如「调音量」同时命中 control 和 query,两组都装载)
        for (AgentDefinition cand : agentRouter.detectAgentCandidates(userMessage, 3)) {
            String g = resolveToolGroupName(cand);
            if (g != null) {
                groups.add(g);
            }
        }
        return groups;
    }

    /**
     * L2 top-k:按多工具组装载 + Observable 包装(替代单组 wrapToolCallbacksByGroup 的多组版)。
     */
    private List<ToolCallback> wrapToolCallbacksByGroups(Set<String> toolGroups, String channelGroup,
                                                         SseEmitter emitter, String sessionId, String subTaskId, String correlationId) {
        List<ToolCallback> callbacks = loadCallbacksByGroupNames(toolGroups);
        // 失败兜底:飘移熔断只读模式 —— open 时过滤不可逆/特权写工具,只留查询/可逆类
        if (driftCircuitBreaker.isOpen(sessionId)) {
            Set<String> dangerous = toolRegistry.getOriginallyDangerousToolNames();
            int before = callbacks.size();
            List<ToolCallback> filtered = callbacks.stream()
                    .filter(cb -> !dangerous.contains(cb.getToolDefinition().name()))
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) {
                log.warn("[DriftBreaker] 会话 {} 只读模式:该组全为不可逆写操作,过滤后无工具,LLM 文本降级 [cid={}]", sessionId, correlationId);
            } else {
                log.warn("[DriftBreaker] 会话 {} 只读模式:过滤写工具 {}→{} [cid={}]", sessionId, before, filtered.size(), correlationId);
            }
            callbacks = filtered;
        }
        log.info("[GroupTools] 工具组集合{}加载 {} 个工具回调(渠道组:{}) [cid={}]", toolGroups, callbacks.size(), channelGroup, correlationId);
        AtomicInteger turnCounter = new AtomicInteger(0);
        return callbacks.stream()
                .map(cb -> (ToolCallback) new ObservableToolCallback(
                        cb, emitter, sessionId, subTaskId,
                        sseEventStore, this::onThinkingTurn, turnCounter, channelGroup,
                        this::rememberEntity, correlationId))
                .collect(Collectors.toList());
    }

    /**
     * 思考轮次处理:持久化思考链 + 飘移熔断信号采集(失败兜底)。
     * <p>ParamSpec 校验失败(observation 含「合法值」= L5 拦截非法参数的特征)= AI 参数飘移直接证据 → recordFailure;
     * 正常调用 → recordSuccess。窗口失败率超阈值后,后续请求进入只读模式。
     */
    private void onThinkingTurn(String sessionId, String subTaskId, int turnIndex,
                                String thought, String actionTool, String actionArgs, String observation) {
        thinkingChainRecorder.record(sessionId, subTaskId, turnIndex, thought, actionTool, actionArgs, observation);
        // 飘移熔断信号(结构化,P0-2 修复):ParamSpec 校验失败(_paramSpecError 标记)= AI 参数飘移 → recordFailure;
        // 工具调用异常(固定前缀"工具调用失败")= 基础设施问题,不计飘移也不污染成功窗口 → 跳过;
        // 其余正常调用 → recordSuccess。
        if (observation != null && observation.contains("_paramSpecError")) {
            driftCircuitBreaker.recordFailure(sessionId);
        } else if (observation != null && observation.startsWith("工具调用失败")) {
            // 工具异常不计入飘移窗口(避免基础设施问题污染成功率)
        } else {
            driftCircuitBreaker.recordSuccess(sessionId);
        }
    }

    /**
     * 生成请求级 correlation ID(失败兜底):8 位短 ID,贯穿日志/SSE/思考链。
     */
    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 从路由结果 Agent 解析工具组名:取 agent.toolGroupName,空则回退 "default"。
     */
    private String resolveToolGroupName(AgentDefinition agent) {
        String g = agent != null ? agent.getToolGroupName() : null;
        return (g == null || g.isBlank()) ? "default" : g;
    }

    /**
     * 根据分组名称查询启用的工具名集合。
     */
    private Set<String> resolveGroupToolNames(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            groupName = "default";
        }
        List<String> toolNames = groupToolConfigRepository.findEnabledToolNamesByGroup(groupName);
        log.info("[GroupTools] 分组 {} 查询到 {} 个工具: {}", groupName, toolNames.size(), toolNames);
        return new HashSet<>(toolNames);
    }

    /**
     * L3 实体记忆回调:解析 resolveDeviceOrGroup 返回,存 deviceKey/groupId(跨轮指代用)。
     * 处理 Spring AI DefaultToolCallResultConverter 的双层 JSON(字符串字面量包装)。
     * 仅当 suggestion=DEVICE_SINGLE/GROUP(唯一命中)时存,AMBIGUOUS 不存。
     */
    private void rememberEntity(String sessionId, String toolName, String toolInput, String toolResult) {
        if (!"resolveDeviceOrGroup".equals(toolName) || sessionId == null || toolResult == null) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(toolResult);
            if (root.isTextual()) {
                root = mapper.readTree(root.asText());
            }
            JsonNode data = root.path("data");
            String suggestion = data.path("suggestion").asText("");
            String displayName = "";
            try {
                JsonNode inputNode = mapper.readTree(toolInput);
                displayName = inputNode.path("target").asText("");
            } catch (Exception ignored) {
            }
            if ("DEVICE_SINGLE".equals(suggestion)) {
                JsonNode dev = data.path("devices").path(0);
                String deviceKey = dev.path("deviceKey").asText("");
                String deviceName = dev.path("deviceName").asText("");
                if (!deviceKey.isEmpty()) {
                    entityMemoryService.remember(sessionId, "device", deviceKey,
                            displayName.isBlank() ? deviceName : displayName, toolName);
                }
            } else if ("GROUP".equals(suggestion)) {
                JsonNode grp = data.path("groups").path(0);
                String groupId = grp.path("groupId").asText("");
                String groupNameVal = grp.path("groupName").asText("");
                if (!groupId.isEmpty()) {
                    entityMemoryService.remember(sessionId, "group", groupId,
                            displayName.isBlank() ? groupNameVal : displayName, toolName);
                }
            }
        } catch (Exception e) {
            log.warn("[EntityMemory] 解析 resolveDeviceOrGroup 结果失败: {}", e.getMessage());
        }
    }

    /**
     * 包装工具回调为可观测版本（统一入口，避免重复代码）。
     */
    private List<ToolCallback> wrapToolCallbacks(SseEmitter emitter, String sessionId, String subTaskId) {
        List<ToolCallback> originalCallbacks = toolRegistry.getToolCallbacks();
        AtomicInteger turnCounter = new AtomicInteger(0);
        return originalCallbacks.stream()
                .map(cb -> (ToolCallback) new ObservableToolCallback(
                        cb, emitter, sessionId, subTaskId,
                        sseEventStore, thinkingChainRecorder::record, turnCounter))
                .collect(Collectors.toList());
    }

    /**
     * 构建子任务的提示词。
     */
    private String buildSubTaskPrompt(String originalMessage, TaskPlanDto.TaskItem taskItem,
                                      Map<Integer, String> existingResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("原始用户消息：").append(originalMessage).append("\n\n");
        prompt.append("当前需要完成的子任务：").append(taskItem.getTitle()).append("\n");

        // 如果有已完成的依赖任务结果，加入上下文
        if (!existingResults.isEmpty()) {
            prompt.append("\n已完成的其他子任务结果：\n");
            existingResults.forEach((idx, result) -> {
                prompt.append("- 子任务").append(idx).append("：").append(result).append("\n");
            });
        }

        prompt.append("\n请完成当前子任务，只返回与当前子任务直接相关的结果。");
        return prompt.toString();
    }

    /**
     * 构建汇总提示词。
     */
    private String buildSummaryPrompt(String originalMessage, TaskPlanDto plan,
                                      Map<Integer, String> taskResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("原始用户消息：").append(originalMessage).append("\n\n");
        prompt.append("以下是各个子任务的执行结果：\n\n");

        for (TaskPlanDto.TaskItem task : plan.getTasks()) {
            String result = taskResults.getOrDefault(task.getIndex(), "（无结果）");
            prompt.append("### 子任务 ").append(task.getIndex() + 1).append("：").append(task.getTitle()).append("\n");
            prompt.append(result).append("\n\n");
        }

        prompt.append("请根据以上所有子任务的结果，综合回答用户的原始问题。回答要完整、有条理。");
        return prompt.toString();
    }

    /**
     * 检测上一轮助手回复是否包含工具调用失败的错误信息，
     * 如果是，则往上下文中注入一条 SYSTEM 消息，提示 LLM 重新调用工具。
     */
    private void injectRetryHintIfNeeded(String sessionId, ConversationContext context) {
        // 「精准防飘移」:只取最近一条 assistant 消息(避免全量 getMessages 拉取整个会话历史)
        ConversationMessage lastAssistant = dialogManager.getLastAssistantMessage(sessionId);
        if (lastAssistant == null || lastAssistant.getContent() == null) {
            return;
        }

        String content = lastAssistant.getContent().toLowerCase();
        if (isToolFailureMessage(content)) {
            String hint = "[系统提示] 用户可能已更换凭证或修复了问题，请重新调用工具获取最新结果，不要复用之前的失败结论。";
            context.addMessage(new SystemMessage(hint));
            log.info("检测到上一轮工具调用失败，已注入重试提示，会话: {}", sessionId);
        }
    }

    /**
     * 判断消息内容是否为工具调用失败。
     */
    private boolean isToolFailureMessage(String content) {
        return content.contains("token") && content.contains("过期")
                || content.contains("认证失败") || content.contains("unauthorized")
                || content.contains("token 已失效") || content.contains("token无效")
                || content.contains("连接失败") || content.contains("connection refused")
                || content.contains("调用失败") || content.contains("call failed")
                || content.contains("401") || content.contains("403")
                || content.contains("超时") || content.contains("timeout");
    }
}
