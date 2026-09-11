package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话消息实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_message")
public class ConversationMessage extends BaseEntity<Long> {

    private String sessionId;
    private String role;
    private String content;
    private String toolCalls;
    private String toolResults;
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer latencyMs;
}
