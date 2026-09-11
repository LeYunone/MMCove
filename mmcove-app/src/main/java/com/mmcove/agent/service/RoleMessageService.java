package com.mmcove.agent.service;

import com.mmcove.agent.common.model.dto.RoleChatRequest;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.common.model.entity.AiRole;
import com.mmcove.agent.common.model.entity.AiRoleChannelGroup;
import com.mmcove.agent.core.prompt.SystemPromptBuilder;
import com.mmcove.agent.infra.persistence.repository.AiChannelRepository;
import com.mmcove.agent.infra.persistence.repository.AiRoleChannelGroupRepository;
import com.mmcove.agent.infra.persistence.repository.AiRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 角色消息服务：模板替换、消息体构造、渠道模型路由。
 * <p>
 * 使用 ChannelLoadBalancer + DynamicChatModelFactory 实现请求级别的模型路由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleMessageService {

    private final AiRoleRepository aiRoleRepository;
    private final AiRoleChannelGroupRepository aiRoleChannelGroupRepository;
    private final AiChannelRepository aiChannelRepository;
    private final ChannelLoadBalancer channelLoadBalancer;
    private final DynamicChatModelFactory dynamicChatModelFactory;
    private final SystemPromptBuilder systemPromptBuilder;

    @Value("${mmcove.ai.language-detection-role:def_language_detector}")
    private String languageDetectionRoleCode;

    /** 「精准防飘移」L3 上下文隔离:非系统消息只保留最近 N 轮(一轮=user+assistant=2 条),默认 6。
     *  与 DialogManager(agent.context.max-rounds)同配置,防多轮历史污染(旧话题/失败结果带偏 AI)。 */
    @Value("${agent.context.max-rounds:6}")
    private int maxRounds;

    /** 可渲染业务组件清单(注入 system prompt,让 AI 知道 renderUi 能渲染什么)。
     *  应与前端 aiComponentRegistry.ts 保持一致,新增组件时同步更新。 */
    private static final String RENDERABLE_COMPONENTS_CATALOG = """

            ===== 可渲染业务组件清单 =====
            需让前端展示业务界面时调用 renderUi 工具,component 取值如下:
            - dashboard       : 工作台概览(设备/内容/课堂统计)
            - device-list     : 设备列表 (props: {keyword?, groupId?})
            - device-detail   : 设备详情 (props 必填: {deviceId, deviceKey?})
            - device-monitor  : 设备实时监控 (props 必填: {deviceKey})
            - resource-lib    : 素材资源库 (props: {scope: "mine"|"public"})
            - user-list       : 用户管理 (props: {keyword?})
            - role-list       : 角色管理 (props: {keyword?})
            - product-list    : 产品管理
            - org-list        : 机构管理 (props: {keyword?})
            - class-connect   : 课堂连接(共享码)
            - class-mgmt      : 班级管理
            - program-list    : 节目列表 (props: {keyword?})
            - user-detail     : 用户详情 (props 必填: {userId})
            - org-tree        : 机构树 (props: {keyword?})
            - resource-detail : 素材详情 (props 必填: {resourceId})
            (设备控制类 device-wallpaper / image-adjust / sound-adjust / screen-lock / send-file 等由 device-detail 内部触发,无需 AI 直接渲染)
            不在清单内的不要调用 renderUi,改用文字告知用户。
            """;

    /**
     * 构建角色聊天上下文并调用 ChatModel 流式输出（带 MCP 工具）。
     * 当分组配置了 MCP 工具时，使用 ChatClient 注入工具实现 ReAct 调用。
     */
    public Flux<String> buildRoleChatContext(RoleChatRequest request, String groupName, List<ToolCallback> tools) {
        if (tools == null || tools.isEmpty()) {
            return buildRoleChatContext(request, groupName);
        }

        RoleChatRequest.RoleInfo roleInfo = request.getRoleInfo();
        String roleCode = roleInfo.getCode();

        AiRole role = aiRoleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleCode));

        ResolvedModel resolved = resolveModel(groupName, roleCode, request.getModel());

        Map<String, String> args = roleInfo.getArgs();
        String systemContent = replaceTemplate(role.getContent(), args);
        String userContent = replaceTemplate(role.getUserContent(), args);

        String languageHint = detectAndBuildLanguageHint(request, resolved);
        if (languageHint != null) {
            systemContent = systemContent + "\n\n" + languageHint;
        }

        List<Message> messages = buildMessages(request, request.getUserAsk(), systemContent, userContent, args);

        log.info("[RoleMessage] 角色聊天(带工具): roleCode={}, modelName={}, groupName={}, msgCount={}, toolCount={}, languageHint={}",
                roleCode, resolved.modelName, groupName, messages.size(), tools.size(), languageHint != null);

        return streamWithClient(messages, resolved, tools);
    }

    /**
     * 构建通用 Agent 聊天上下文（无角色模板版本），供 handleAgentChatSse 调用。
     * 与 buildRoleChatContext 使用相同的动态路由渠道 + 动态构建 ChatClient + 注入工具链路。
     *
     * <p>模型路由优先级：
     * <ol>
     *   <li>请求体指定的 model</li>
     *   <li>角色配置的默认模型（ai_role.model），仅当请求未指定 model 且携带 roleInfo 时生效</li>
     *   <li>分组默认渠道（resolveModel 内部 selectDefaultByGroup 回退）</li>
     * </ol>
     *
     * @param messages  Spring AI Message 列表（由 Controller 从 OpenAI 格式转换而来）
     * @param groupName Token 分组名称
     * @param model     请求指定的模型名（可为空）
     * @param tools     分组对应的 MCP 工具列表（可为空）
     * @param roleInfo  流式路径可选的角色信息（code + args），用于注入角色系统提示词与模型回退
     * @return 流式响应内容（Flux<String>，每个元素是一个文本片段）
     */
    public Flux<String> buildAgentChatContext(List<Message> messages, String groupName,
                                              String model, List<ToolCallback> tools,
                                              RoleChatRequest.RoleInfo roleInfo) {
        // 查询角色一次，供模型回退和提示词注入共用；角色缺失时仅记日志，不阻断流程
        AiRole role = null;
        if (roleInfo != null && roleInfo.getCode() != null && !roleInfo.getCode().isEmpty()) {
            role = aiRoleRepository.findByCode(roleInfo.getCode()).orElse(null);
            if (role == null) {
                log.warn("[RoleMessage] 流式路径角色不存在，跳过注入与模型回退: code={}", roleInfo.getCode());
            }
        }

        // 模型回退：请求未指定 model 时，使用角色配置的默认模型路由渠道
        String effectiveModel = model;
        if ((effectiveModel == null || effectiveModel.isEmpty())
                && role != null && role.getModel() != null && !role.getModel().isEmpty()) {
            effectiveModel = role.getModel();
            log.info("[RoleMessage] 流式路径未指定模型，回退到角色默认模型: roleCode={}, model={}",
                    roleInfo.getCode(), effectiveModel);
        }

        // 按需注入角色系统提示词（与已有 system 合并）
        if (role != null) {
            injectRoleSystemContent(messages, role, roleInfo);
        }

        ResolvedModel resolved = resolveModel(groupName, null, effectiveModel);
        log.info("[RoleMessage] 通用聊天: modelName={}, groupName={}, msgCount={}, toolCount={}, roleCode={}",
                resolved.modelName, groupName, messages.size(), tools != null ? tools.size() : 0,
                roleInfo != null ? roleInfo.getCode() : null);
        return streamWithClient(messages, resolved, tools);
    }

    /**
     * 流式路径注入角色系统提示词：args 替换 → 与 messages[0] 的 SystemMessage 合并（角色在前）。
     * 角色无 content 时仅记日志，不阻断流程。调用方需已查询角色，避免重复查表。
     */
    private void injectRoleSystemContent(List<Message> messages, AiRole role,
                                         RoleChatRequest.RoleInfo roleInfo) {
        if (role.getContent() == null || role.getContent().isEmpty()) {
            log.warn("[RoleMessage] 流式路径角色无系统提示词: code={}", roleInfo.getCode());
            return;
        }
        String roleContent = replaceTemplate(role.getContent(), roleInfo.getArgs());

        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage sm) {
            // 合并：角色提示词 + 空行 + 前端已有 system（保留 systemPrompt + seed 上下文）
            messages.set(0, new SystemMessage(roleContent + "\n\n" + sm.getText()));
        } else {
            messages.add(0, new SystemMessage(roleContent));
        }
        log.info("[RoleMessage] 流式路径注入角色系统提示词: roleCode={}, merged={}",
                roleInfo.getCode(), !messages.isEmpty() && messages.get(0) instanceof SystemMessage);
    }

    /**
     * 共享核心：根据渠道类型动态构建 ChatClient，分离系统消息，注入工具，流式输出。
     * 提取自 buildRoleChatContext(request, groupName, tools)，保证两条链路行为一致。
     */
    private Flux<String> streamWithClient(List<Message> messages, ResolvedModel resolved,
                                          List<ToolCallback> tools) {
        // 构建 ChatClient 并根据渠道类型注入模型名
        ChatClient.Builder clientBuilder = ChatClient.builder(resolved.chatModel);
        if (resolved.modelName != null) {
            // Spring AI 2.0: defaultOptions 接收 ChatOptions.Builder；仅覆盖模型名即可路由到正确模型
            clientBuilder.defaultOptions(
                    org.springframework.ai.chat.prompt.ChatOptions.builder().model(resolved.modelName));
        }
        ChatClient client = clientBuilder.build();

        // 分离系统消息和聊天消息
        String systemText = null;
        List<Message> chatMessages = new ArrayList<>();
        for (Message msg : messages) {
            if (msg instanceof SystemMessage sm) {
                systemText = sm.getText();
            } else {
                chatMessages.add(msg);
            }
        }
        // 追加可渲染组件清单,让 AI 知道有哪些组件可渲染
        if (systemText == null) {
            systemText = "";
        }
        systemText = systemText + "\n\n" + RENDERABLE_COMPONENTS_CATALOG
                + systemPromptBuilder.getCapabilityBoundary();

        // 「精准防飘移」L3 上下文隔离:非系统消息只保留最近 maxRounds*2 条,防多轮历史污染
        // (旧话题/失败结果/过期数据带偏 AI)。system 不截断(始终保留)。
        int maxNonSys = maxRounds * 2;
        if (chatMessages.size() > maxNonSys) {
            log.info("[RoleMessage] 上下文窗口隔离: {}→{} 条(保留最近 {} 轮)",
                    chatMessages.size(), maxNonSys, maxRounds);
            chatMessages = new ArrayList<>(chatMessages.subList(chatMessages.size() - maxNonSys, chatMessages.size()));
        }

        ChatClient.ChatClientRequestSpec spec = client.prompt();
        if (systemText != null) {
            spec = spec.system(systemText);
        }
        if (!chatMessages.isEmpty()) {
            spec = spec.messages(chatMessages);
        }
        if (tools != null && !tools.isEmpty()) {
            spec = spec.toolCallbacks(tools.toArray(new ToolCallback[0]));
        }

        return spec.stream()
                .content()
                .mapNotNull(text -> (text != null && !text.isEmpty()) ? text : null);
    }

    /**
     * 构建角色聊天上下文并调用 ChatModel 流式输出。
     *
     * @param request   角色聊天请求
     * @param groupName Token 分组名称
     * @return 流式响应内容（Flux<String>，每个元素是一个文本片段）
     */
    public Flux<String> buildRoleChatContext(RoleChatRequest request, String groupName) {

        RoleChatRequest.RoleInfo roleInfo = request.getRoleInfo();
        String roleCode = roleInfo.getCode();

        // 1. 查角色模板
        AiRole role = aiRoleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleCode));

        // 2. 查渠道关联，路由到实际模型
        ResolvedModel resolved = resolveModel(groupName, roleCode, request.getModel());

        // 3. 模板占位符替换
        Map<String, String> args = roleInfo.getArgs();
        String systemContent = replaceTemplate(role.getContent(), args);
        String userContent = replaceTemplate(role.getUserContent(), args);

        String languageHint = detectAndBuildLanguageHint(request, resolved);
        if (languageHint != null) {
            systemContent = systemContent + "\n\n" + languageHint;
        }

        // 4. 构造消息列表
        List<Message> messages = buildMessages(request, request.getUserAsk(), systemContent, userContent, args);

        log.info("[RoleMessage] 角色聊天: roleCode={}, modelName={}, groupName={}, msgCount={}",
                roleCode, resolved.modelName, groupName, messages.size());

        // 5. 构建 Prompt（带模型名称覆盖）
        Prompt prompt = buildPrompt(messages, resolved);

        // 6. 流式调用 ChatModel
        ChatModel chatModel = resolved.chatModel;
        if (chatModel instanceof StreamingChatModel streaming) {
            return streaming.stream(prompt)
                    .mapNotNull(response -> {
                        if (response.getResult() != null && response.getResult().getOutput() != null) {
                            String text = response.getResult().getOutput().getText();
                            return (text != null && !text.isEmpty()) ? text : null;
                        }
                        return null;
                    });
        }

        // Fallback：同步调用（兼容不支持流式的 ChatModel）
        return Flux.defer(() -> {
            ChatResponse response = chatModel.call(prompt);
            String text = "";
            if (response.getResult() != null && response.getResult().getOutput() != null) {
                text = response.getResult().getOutput().getText();
            }
            return Flux.just(text != null ? text : "");
        });
    }

    /**
     * 构建 Prompt，根据渠道所属的 ChatModel 类型创建对应的 ChatOptions 来覆盖模型名。
     */
    private Prompt buildPrompt(List<Message> messages, ResolvedModel resolved) {
        if (resolved.modelName == null) {
            return new Prompt(messages);
        }

        // 根据渠道类型创建对应类型的 ChatOptions
        int channelType = resolved.channelType;
        if (channelType == AiChannel.TYPE_ANTHROPIC || channelType == AiChannel.TYPE_ZHIPU) {
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .model(resolved.modelName)
                    .build();
            return new Prompt(messages, options);
        } else if (channelType == AiChannel.TYPE_OPENAI) {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(resolved.modelName)
                    .build();
            return new Prompt(messages, options);
        }

        // 其他类型不加 options，使用默认模型
        return new Prompt(messages);
    }

    /**
     * 通过 LLM 检测用户语言并生成语言强制指令。
     * 提示词和语言映射全部从角色表加载（def_language_detector），方便运维调整。
     * 角色的 content = 系统提示词（检测指令 + 语言映射规则）
     * 角色的 userContent = 用户消息模板（支持 {{text}} 占位符）
     * 模型返回 "NATIVE" 表示中文（无需追加提示），其他返回值直接追加到系统消息。
     */
    private String detectAndBuildLanguageHint(RoleChatRequest request, ResolvedModel resolved) {
        String userText = extractLastUserText(request);
        if (userText == null || userText.isEmpty()) return null;

        try {
            AiRole langRole = aiRoleRepository.findByCode(languageDetectionRoleCode).orElse(null);
            if (langRole == null) {
                log.debug("[RoleMessage] 语言检测角色未配置: code={}", languageDetectionRoleCode);
                return null;
            }

            String truncated = userText.length() > 200 ? userText.substring(0, 200) : userText;
            String userTemplate = langRole.getUserContent() != null ? langRole.getUserContent() : "{{text}}";
            String userPrompt = userTemplate.replace("{{text}}", truncated);

            List<Message> messages = new ArrayList<>();
            if (langRole.getContent() != null && !langRole.getContent().isEmpty()) {
                messages.add(new SystemMessage(langRole.getContent()));
            }
            messages.add(new UserMessage(userPrompt));

            Prompt prompt = buildPrompt(messages, resolved);
            ChatResponse response = resolved.chatModel().call(prompt);
            String result = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText() : null;

            if (result != null) {
                result = result.trim();
                if (result.isEmpty() || "NATIVE".equalsIgnoreCase(result)) {
                    return null;
                }
                log.info("[RoleMessage] 语言检测: role={}, hint={}", languageDetectionRoleCode,
                        result.length() > 80 ? result.substring(0, 80) + "..." : result);
                return result;
            }
            return null;
        } catch (Exception e) {
            log.warn("[RoleMessage] 语言检测失败，跳过语言提示: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取用户最后的输入文本（优先 userAsk，其次从消息历史中取最后一条 user 消息）。
     */
    private String extractLastUserText(RoleChatRequest request) {
        String userText = request.getUserAsk();
        if (userText == null || userText.isEmpty()) {
            if (request.getMessages() != null) {
                for (int i = request.getMessages().size() - 1; i >= 0; i--) {
                    RoleChatRequest.RoleChatMessage msg = request.getMessages().get(i);
                    if ("user".equalsIgnoreCase(msg.getRole())) {
                        userText = msg.getContentAsString();
                        break;
                    }
                }
            }
        }
        return userText;
    }

    /**
     * 构造消息列表。
     */
    private List<Message> buildMessages(RoleChatRequest request, String userAsk,
                                        String systemContent, String userContent,
                                        Map<String, String> args) {
        List<Message> messages = new ArrayList<>();

        if (systemContent != null && !systemContent.isEmpty()) {
            messages.add(new SystemMessage(systemContent));
        }

        if (request.getMessages() != null) {
            for (RoleChatRequest.RoleChatMessage msg : request.getMessages()) {
                switch (msg.getRole().toLowerCase()) {
                    case "user" -> messages.add(toUserMessage(msg, args));
                    case "assistant" -> messages.add(new AssistantMessage(msg.getContentAsString()));
                    default -> log.warn("[RoleMessage] 忽略未知角色消息: role={}", msg.getRole());
                }
            }
        }

        if (userContent != null && !userContent.isEmpty()) {
            messages.add(new UserMessage(userContent));
        }

        if (userAsk != null && !userAsk.isEmpty()) {
            messages.add(new UserMessage(userAsk));
        }

        return messages;
    }

    /**
     * 解析实际模型：优先使用 ai_ability 路由，回退到旧逻辑。
     */
    private ResolvedModel resolveModel(String groupName, String roleCode, String requestedModel) {
        if (groupName == null || groupName.isEmpty()) {
            groupName = "default";
        }

        // 确定模型名称：优先使用请求中的 model，否则从角色渠道关联获取
        String modelName = requestedModel;
        AiChannel channel = null;

        if (modelName != null && !modelName.isEmpty()) {
            // 通过 ChannelLoadBalancer 按 model 路由（已内置模型映射）
            ChannelLoadBalancer.SelectedChannel selected = channelLoadBalancer.selectChannel(groupName, modelName, 0);
            if (selected != null) {
                channel = selected.channel();
                // ChannelLoadBalancer 已处理 modelMapping，直接使用映射后的模型名
                modelName = selected.model();
            }
        } else {
            // 未指定 model 时,取当前分组下优先级最高的能力作为默认渠道与模型,
            // 使 /v1/chat/completions 无 model 入参也能命中渠道管理中配置的渠道。
            ChannelLoadBalancer.SelectedChannel selected = channelLoadBalancer.selectDefaultByGroup(groupName);
            if (selected != null) {
                channel = selected.channel();
                modelName = selected.model();
                log.info("[RoleMessage] 未指定模型,使用分组默认: group={}, model={}, channelId={}",
                        groupName, modelName, channel.getId());
            }
        }

        if (channel == null) {
            // 回退到旧的 ai_role_channel_group 路由
            ResolvedModel fallback = resolveModelLegacy(groupName, roleCode);
            if (fallback != null) {
                return fallback;
            }
            // 最终回退到系统默认
            return resolveDefault();
        }

        ChatModel chatModel = dynamicChatModelFactory.getOrCreate(channel);
        log.debug("[RoleMessage] 路由模型: channelId={}, modelName={}, channelType={}",
                channel.getId(), modelName, channel.getType());

        return new ResolvedModel(modelName, channel.getType(), chatModel, channel.getId());
    }

    /**
     * 旧的路由逻辑（兼容 ai_role_channel_group）。
     */
    private ResolvedModel resolveModelLegacy(String groupName, String roleCode) {
        List<AiRoleChannelGroup> groups = aiRoleChannelGroupRepository
                .findByGroupAndRoleCode(groupName, roleCode);

        if (groups.isEmpty()) {
            return null;
        }

        Long channelId = groups.get(0).getChannelId();
        AiChannel channel = aiChannelRepository.findById(channelId).orElse(null);

        if (channel == null || channel.getModels() == null || channel.getModels().isEmpty()) {
            return null;
        }

        String[] modelArr = channel.getModels().split(",");
        String modelName = modelArr[modelArr.length - 1].trim();
        int channelType = determineChannelType(modelName);
        ChatModel chatModel;

        if (channel.getApiKey() != null && !channel.getApiKey().isEmpty()) {
            chatModel = dynamicChatModelFactory.getOrCreate(channel);
        } else {
            // 旧渠道无 apiKey，使用系统默认 ChatModel
            String beanName = channelType == AiChannel.TYPE_OPENAI ? "openAiChatModel" : "anthropicChatModel";
            chatModel = dynamicChatModelFactory.getSystemDefault(beanName);
        }

        return new ResolvedModel(modelName, channelType, chatModel, channel.getId());
    }

    /**
     * 应用渠道的模型名映射。
     */
    private String applyModelMapping(AiChannel channel, String modelName) {
        String mapping = channel.getModelMapping();
        if (mapping == null || mapping.isEmpty()) {
            return modelName;
        }
        try {
            Map<String, String> mappingMap = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(mapping, new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            return mappingMap.getOrDefault(modelName, modelName);
        } catch (Exception e) {
            log.warn("[RoleMessage] 解析模型映射失败: {}", e.getMessage());
            return modelName;
        }
    }

    /**
     * 根据模型名判断渠道类型。
     */
    private int determineChannelType(String modelName) {
        if (modelName == null) {
            return AiChannel.TYPE_ANTHROPIC;
        }
        String lower = modelName.toLowerCase();
        if (lower.startsWith("glm-") || lower.contains("zhipu")) {
            return AiChannel.TYPE_ANTHROPIC;
        }
        if (lower.startsWith("gpt-") || lower.startsWith("o1-") || lower.startsWith("o3-")) {
            return AiChannel.TYPE_OPENAI;
        }
        if (lower.startsWith("claude")) {
            return AiChannel.TYPE_ANTHROPIC;
        }
        return AiChannel.TYPE_ANTHROPIC;
    }

    /**
     * 回退到默认 ChatModel。
     */
    private ResolvedModel resolveDefault() {
        ChatModel defaultModel = dynamicChatModelFactory.getSystemDefault();
        return new ResolvedModel(null, AiChannel.TYPE_OPENAI, defaultModel, null);
    }

    /**
     * 将 RoleChatMessage 转为 Spring AI UserMessage，支持多模态（文本+图片）。
     */
    private UserMessage toUserMessage(RoleChatRequest.RoleChatMessage msg, Map<String, String> args) {
        Object content = msg.getContent();
        if (content instanceof String s) {
            return new UserMessage(replaceTemplate(s, args));
        }
        if (content instanceof List<?> list && !list.isEmpty()) {
            String text = null;
            List<Media> mediaList = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                String type = map.get("type") instanceof String t ? t : "";
                if ("text".equals(type)) {
                    Object textVal = map.get("text");
                    text = textVal instanceof String t ? replaceTemplate(t, args) : null;
                } else if ("image_url".equals(type) && map.get("image_url") instanceof Map<?, ?> imageUrlMap) {
                    String url = imageUrlMap.get("url") instanceof String u ? replaceTemplate(u, args) : null;
                    if (url != null && !url.isEmpty()) {
                        try {
                            mediaList.add(createMedia(url));
                        } catch (Exception e) {
                            log.warn("[RoleMessage] 图片加载失败: url={}, error={}", url.substring(0, Math.min(60, url.length())), e.getMessage());
                        }
                    }
                }
            }
            if (!mediaList.isEmpty()) {
                return UserMessage.builder()
                        .text(text != null ? text : "")
                        .media(mediaList.toArray(new Media[0]))
                        .build();
            }
            return new UserMessage(text != null ? text : msg.getContentAsString());
        }
        return new UserMessage(msg.getContentAsString());
    }

    /**
     * 根据图片URL创建 Media 对象。支持 http(s) URL 和 data:image/...;base64,... 格式。
     */
    @SuppressWarnings("unchecked")
    private Media createMedia(String url) throws Exception {
        if (url.startsWith("data:")) {
            int semicolon = url.indexOf(';');
            int comma = url.indexOf(',');
            MimeType mimeType = MimeType.valueOf(url.substring(5, semicolon));
            String base64Data = url.substring(comma + 1);
            byte[] bytes = Base64.getDecoder().decode(base64Data);
            return new Media(mimeType, new org.springframework.core.io.ByteArrayResource(bytes));
        }
        MimeType mimeType = guessImageMimeType(url);
        return new Media(mimeType, URI.create(url));
    }

    /**
     * 根据 URL 后缀猜测图片 MIME 类型。
     */
    private MimeType guessImageMimeType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return MimeTypeUtils.IMAGE_PNG;
        if (lower.contains(".gif")) return MimeTypeUtils.IMAGE_GIF;
        if (lower.contains(".webp")) return MimeType.valueOf("image/webp");
        return MimeTypeUtils.IMAGE_JPEG;
    }

    /**
     * 替换模板中的 {{key}} 占位符。
     */
    private String replaceTemplate(String template, Map<String, String> args) {
        if (template == null || template.isEmpty() || args == null || args.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : args.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /**
     * 解析后的模型信息。
     */
    public record ResolvedModel(String modelName, int channelType, ChatModel chatModel, Long channelId) {
    }
}
