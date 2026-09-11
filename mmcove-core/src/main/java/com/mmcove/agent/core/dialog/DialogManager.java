package com.mmcove.agent.core.dialog;

import com.mmcove.agent.common.enums.MessageRole;
import com.mmcove.agent.common.model.entity.Conversation;
import com.mmcove.agent.common.model.entity.ConversationMessage;
import com.mmcove.agent.infra.persistence.repository.ConversationRepository;
import com.mmcove.agent.infra.persistence.repository.MessageRepository;
import com.mmcove.agent.llm.memory.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 对话管理器，负责上下文加载和保存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialogManager {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ContextManager contextManager;
    private final EntityMemoryService entityMemoryService;

    /** 「精准防飘移」上下文隔离:加载最近 N 轮(一轮 = user + assistant = 2 条),默认 6 轮。
     *  可通过配置 agent.context.max-rounds 调整。避免全量历史导致「会话越长 AI 越偏」。 */
    @Value("${agent.context.max-rounds:6}")
    private int maxRounds;

    /**
     * 根据会话 ID 加载对话上下文。
     */
    public ConversationContext loadContext(String sessionId) {
        ConversationContext context = ConversationContext.builder()
                .sessionId(sessionId)
                .maxRounds(maxRounds)
                .build();

        // 「精准防飘移」上下文隔离:只加载最近 maxRounds 轮(= maxRounds*2 条 user+assistant 消息),
        // 而非全量 findBySessionId。避免会话越长、历史越多,导致 AI 被旧话题/过期数据/失败结果带偏。
        // 全量历史仍可通过 getMessages(sessionId) 查询(供前端回显),职责分离。
        int lastN = maxRounds * 2;
        List<ConversationMessage> messages = messageRepository.findRecentBySessionId(sessionId, lastN);
        for (ConversationMessage cm : messages) {
            context.addMessage(convertToSpringMessage(cm));
        }

        // L3 实体感知记忆:注入本会话已解析的 deviceKey/groupId,解决跨轮指代(「它/这台」)
        injectEntityMemory(sessionId, context);

        return context;
    }

    /**
     * 注入已解析实体为 system message,让 LLM 跨轮能命中 deviceKey/groupId。
     * 实体过期由 EntityMemoryService.findActiveBySession 过滤(过期不注入)。
     */
    private void injectEntityMemory(String sessionId, ConversationContext context) {
        var entities = entityMemoryService.getActiveEntities(sessionId);
        if (entities == null || entities.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder(
                "[已解析实体记忆] 本会话已确认的设备/分组目标(用户说「它/这台/这个」时,优先用这些已确认的 Key,除非用户明确换了目标):\n");
        for (var e : entities) {
            sb.append("- ").append(e.getDisplayName()).append(" → ")
                    .append("device".equals(e.getEntityType()) ? "deviceKey " : "groupId ")
                    .append(e.getEntityKey()).append("\n");
        }
        context.addMessage(new SystemMessage(sb.toString()));
    }

    /**
     * 保存对话上下文。
     */
    public void saveContext(String sessionId, ConversationContext context) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseGet(() -> createNewConversation(sessionId, null, null, null));
        conversationRepository.update(conversation);
    }

    /**
     * 追加一条消息到对话中。
     */
    public void appendMessage(String sessionId, MessageRole role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setSessionId(sessionId);
        message.setRole(role.name().toLowerCase());
        message.setContent(content);
        messageRepository.save(message);
        log.debug("已追加消息到会话 {}: role={}", sessionId, role);
    }

    /**
     * 创建新会话，自动生成会话 ID。
     */
    public String createNewSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        createNewConversation(sessionId, null, null, null);
        return sessionId;
    }

    /**
     * 创建新会话，指定标题（Agent 由路由自动绑定）。
     */
    public String createNewSession(String title) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        createNewConversation(sessionId, null, title, null);
        return sessionId;
    }

    /**
     * 创建新会话，指定标题和用户 ID。
     */
    public String createUserSession(String title, String userId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        createNewConversation(sessionId, null, title, userId);
        return sessionId;
    }

    /**
     * 用调用方提供的 sessionId 创建归属用户的会话(供前端已持有 sessionId 的场景:
     * 用前端 id 建,前后端 id 一致,无需回传)。若已存在同 id 会话则不覆盖。
     */
    public String createUserSessionWithId(String sessionId, String userId) {
        if (conversationRepository.findBySessionId(sessionId).isPresent()) {
            return sessionId;
        }
        createNewConversation(sessionId, null, null, userId);
        return sessionId;
    }

    /**
     * 创建新会话，指定 Agent 和标题。
     */
    public String createNewSession(String agentId, String title) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        createNewConversation(sessionId, agentId, title, null);
        return sessionId;
    }

    /**
     * 列出所有活跃会话。
     */
    public List<Conversation> listSessions() {
        return conversationRepository.findAllActive();
    }

    /**
     * 列出指定用户的活跃会话。
     */
    public List<Conversation> listSessionsByUser(String userId) {
        return conversationRepository.findByUserId(userId);
    }

    /**
     * 更新会话信息。
     */
    public void updateConversation(Conversation conversation) {
        conversationRepository.update(conversation);
    }

    /**
     * 删除会话（软删除：修改状态）。
     */
    public void deleteSession(String sessionId) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId).orElse(null);
        if (conversation != null) {
            conversation.setStatus("DELETED");
            conversationRepository.update(conversation);
            messageRepository.deleteBySessionId(sessionId);
            entityMemoryService.forgetSession(sessionId);
            log.info("已删除会话: {}", sessionId);
        }
    }

    /**
     * 获取会话信息。
     */
    public Conversation getSession(String sessionId) {
        return conversationRepository.findBySessionId(sessionId).orElse(null);
    }

    /**
     * 获取会话消息历史。
     */
    public List<ConversationMessage> getMessages(String sessionId) {
        return messageRepository.findBySessionId(sessionId);
    }

    /**
     * 取会话最近一条 assistant 消息(供重试提示判断用,避免全量拉取)。
     */
    public ConversationMessage getLastAssistantMessage(String sessionId) {
        return messageRepository.findLastAssistantBySessionId(sessionId);
    }

    private Conversation createNewConversation(String sessionId, String agentId, String title, String userId) {
        Conversation conversation = new Conversation();
        conversation.setSessionId(sessionId);
        conversation.setStatus("ACTIVE");
        conversation.setTenantId(0L);
        conversation.setUserId(userId);
        conversation.setAgentType(agentId);
        conversation.setTitle(title != null ? title : "新对话");
        return conversationRepository.save(conversation);
    }

    private Message convertToSpringMessage(ConversationMessage cm) {
        return switch (cm.getRole().toUpperCase()) {
            case "USER" -> new UserMessage(cm.getContent());
            case "SYSTEM" -> new SystemMessage(cm.getContent());
            default -> new AssistantMessage(cm.getContent());
        };
    }
}
