package com.mmcove.agent.llm.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;

/**
 * 会话上下文数据结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    private String sessionId;
    private String agentId;
    private String systemPrompt;
    @Builder.Default
    private List<Message> messages = new ArrayList<>();
    private int maxRounds;

    public void addMessage(Message message) {
        this.messages.add(message);
    }
}
