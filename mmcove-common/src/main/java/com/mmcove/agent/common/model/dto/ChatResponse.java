package com.mmcove.agent.common.model.dto;

import com.mmcove.agent.common.enums.ChatResponseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private ChatResponseType type;
    private String content;
    private String toolName;
    private Object toolResult;
    private String error;
}
