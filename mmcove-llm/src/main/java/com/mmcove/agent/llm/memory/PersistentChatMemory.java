package com.mmcove.agent.llm.memory;

import com.mmcove.agent.common.model.entity.ConversationMessage;
import com.mmcove.agent.infra.persistence.repository.MessageRepository;
import com.mmcove.agent.infra.redis.ConversationMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 持久化聊天记忆，实现 Spring AI 的 ChatMemory 接口。
 * MySQL 存储历史记录 + Redis 存储热会话缓存（二级缓存）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentChatMemory implements ChatMemory {

    private final MessageRepository messageRepository;
    private final ConversationMemoryStore memoryStore;

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            ConversationMessage entity = new ConversationMessage();
            entity.setSessionId(conversationId);
            entity.setRole(message.getMessageType().getValue());
            entity.setContent(message.getText());
            messageRepository.save(entity);
        }
        // 更新 Redis 缓存
        List<ConversationMessage> allMessages = messageRepository.findBySessionId(conversationId);
        memoryStore.saveMessages(conversationId, allMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        // 先尝试从 Redis 缓存获取
        List<ConversationMessage> cached = memoryStore.loadMessages(conversationId);
        List<ConversationMessage> messages;

        if (cached != null && !cached.isEmpty()) {
            messages = cached;
        } else {
            messages = messageRepository.findBySessionId(conversationId);
            if (!messages.isEmpty()) {
                memoryStore.saveMessages(conversationId, messages);
            }
        }

        List<Message> result = new ArrayList<>();
        for (ConversationMessage cm : messages) {
            result.add(convertToMessage(cm));
        }
        return result;
    }

    @Override
    public void clear(String conversationId) {
        memoryStore.evict(conversationId);
        messageRepository.deleteBySessionId(conversationId);
    }

    private Message convertToMessage(ConversationMessage cm) {
        String role = cm.getRole();
        String content = cm.getContent();
        return switch (role.toUpperCase()) {
            case "USER" -> new UserMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new AssistantMessage(content);
        };
    }
}
