package com.mmcove.agent.controller;

import com.mmcove.agent.common.context.UserSessionContext;
import com.mmcove.agent.common.model.dto.*;
import com.mmcove.agent.common.model.entity.Conversation;
import com.mmcove.agent.common.model.entity.ConversationMessage;
import com.mmcove.agent.core.dialog.DialogManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理控制器。
 *
 * <p>userId 来源:优先读请求头 {@code X-Platform-User-Id}(综合业务平台用户 id),
 * 缺失时回退到 {@link UserSessionContext#getUserId()}。
 * 这样前端调用时传平台 userId 即可让会话按平台用户归属/隔离。
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final DialogManager dialogManager;

    private static final String PLATFORM_USER_ID_HEADER = "X-Platform-User-Id";

    /**
     * 当前用户 id:优先 X-Platform-User-Id 头(平台 userId),回退 UserSessionContext。
     */
    private String currentUserId(HttpServletRequest request) {
        String platformUserId = request.getHeader(PLATFORM_USER_ID_HEADER);
        if (platformUserId != null && !platformUserId.isEmpty()) {
            return platformUserId;
        }
        return UserSessionContext.get().getUserId();
    }

    /**
     * 创建会话。
     */
    @PostMapping
    public ApiResponse<SessionResponse> createSession(@RequestBody CreateSessionRequest request,
                                                      HttpServletRequest httpRequest) {
        String userId = currentUserId(httpRequest);
        String sessionId = dialogManager.createUserSession(request.getTitle(), userId);

        Conversation conversation = dialogManager.getSession(sessionId);
        return ApiResponse.success(toSessionResponse(conversation));
    }

    /**
     * 列出当前用户的活跃会话。
     */
    @GetMapping
    public ApiResponse<List<SessionResponse>> listSessions(HttpServletRequest httpRequest) {
        String userId = currentUserId(httpRequest);
        List<Conversation> conversations = dialogManager.listSessionsByUser(userId);
        return ApiResponse.success(conversations.stream()
                .map(this::toSessionResponse)
                .toList());
    }

    /**
     * 获取会话信息。
     */
    @GetMapping("/{sessionId}")
    public ApiResponse<SessionResponse> getSession(@PathVariable String sessionId,
                                                   HttpServletRequest httpRequest) {
        Conversation conversation = dialogManager.getSession(sessionId);
        if (conversation == null) {
            return ApiResponse.error(404, "会话不存在");
        }
        ApiResponse<Void> ownershipCheck = checkOwnership(conversation, httpRequest);
        if (ownershipCheck != null) {
            return (ApiResponse) ownershipCheck;
        }
        return ApiResponse.success(toSessionResponse(conversation));
    }

    /**
     * 获取会话消息历史。
     */
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<MessageResponse>> getMessages(@PathVariable String sessionId,
                                                          HttpServletRequest httpRequest) {
        Conversation conversation = dialogManager.getSession(sessionId);
        if (conversation == null) {
            return ApiResponse.error(404, "会话不存在");
        }
        ApiResponse<Void> ownershipCheck = checkOwnership(conversation, httpRequest);
        if (ownershipCheck != null) {
            return (ApiResponse) ownershipCheck;
        }

        List<ConversationMessage> messages = dialogManager.getMessages(sessionId);
        return ApiResponse.success(messages.stream()
                .map(this::toMessageResponse)
                .toList());
    }

    /**
     * 删除会话。
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId,
                                           HttpServletRequest httpRequest) {
        Conversation conversation = dialogManager.getSession(sessionId);
        if (conversation == null) {
            return ApiResponse.error(404, "会话不存在");
        }
        ApiResponse<Void> ownershipCheck = checkOwnership(conversation, httpRequest);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        dialogManager.deleteSession(sessionId);
        return ApiResponse.success();
    }

    /**
     * 更新会话标题。
     */
    @PutMapping("/{sessionId}/title")
    public ApiResponse<Void> updateTitle(@PathVariable String sessionId,
                                         @RequestBody java.util.Map<String, String> body,
                                         HttpServletRequest httpRequest) {
        String title = body.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ApiResponse.error(400, "标题不能为空");
        }
        Conversation conversation = dialogManager.getSession(sessionId);
        if (conversation == null) {
            return ApiResponse.error(404, "会话不存在");
        }
        ApiResponse<Void> ownershipCheck = checkOwnership(conversation, httpRequest);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        conversation.setTitle(title.trim());
        dialogManager.updateConversation(conversation);
        return ApiResponse.success();
    }

    /**
     * 校验会话归属当前用户。
     * @return 非 null 表示校验失败,直接返回错误响应
     */
    private ApiResponse<Void> checkOwnership(Conversation conversation, HttpServletRequest httpRequest) {
        String currentUserId = currentUserId(httpRequest);
        if (currentUserId == null) {
            return ApiResponse.error(401, "未登录");
        }
        String ownerUserId = conversation.getUserId();
        if (ownerUserId != null && !currentUserId.equals(ownerUserId)) {
            return ApiResponse.error(403, "无权访问该会话");
        }
        return null;
    }

    private SessionResponse toSessionResponse(Conversation c) {
        return SessionResponse.builder()
                .sessionId(c.getSessionId())
                .title(c.getTitle())
                .agentType(c.getAgentType())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(ConversationMessage m) {
        return MessageResponse.builder()
                .id(m.getId())
                .sessionId(m.getSessionId())
                .role(m.getRole())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
