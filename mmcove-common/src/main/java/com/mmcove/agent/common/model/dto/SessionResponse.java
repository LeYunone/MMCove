package com.mmcove.agent.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话信息响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private String sessionId;
    private String title;
    private String agentType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
