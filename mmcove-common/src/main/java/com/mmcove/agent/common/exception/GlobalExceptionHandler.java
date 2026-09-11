package com.mmcove.agent.common.exception;

import com.mmcove.agent.common.model.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 * 对于SSE请求（text/event-stream），异常由ChatController内部通过SseEmitter处理，
 * 此处不做二次响应，避免Content-Type冲突导致前端收到404或乱码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            log.warn("SSE请求业务异常（已由SseEmitter处理）: code={}, message={}", e.getCode(), e.getMessage());
            return null;
        }
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(LlmCallException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmCallException(LlmCallException e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            log.error("SSE请求LLM调用失败（已由SseEmitter处理）: {}", e.getMessage());
            return null;
        }
        log.error("LLM 调用失败: {}", e.getMessage(), e);
        return ResponseEntity.ok(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleToolExecutionException(ToolExecutionException e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            log.error("SSE请求工具执行失败（已由SseEmitter处理）: {}", e.getMessage());
            return null;
        }
        log.error("工具执行失败: {}", e.getMessage(), e);
        return ResponseEntity.ok(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(TokenAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenAuthException(TokenAuthException e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            return null;
        }
        log.warn("Token认证异常: code={}, message={}", e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus()).body(ApiResponse.error(e.getHttpStatus(), e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            return null;
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            log.error("SSE请求未知异常（已由SseEmitter处理）: {}", e.getMessage());
            return null;
        }
        log.error("未知异常: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(500, "服务器内部错误"));
    }

    /**
     * ResponseStatusException（自建用户体系抛出：401 未登录/密码错误、409 用户名冲突等）。
     * 按异常自带的状态码返回，避免被兜底 500 吞掉。
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            return null;
        }
        int status = e.getStatusCode().value();
        String message = e.getReason() != null ? e.getReason() : e.getMessage();
        log.warn("ResponseStatus 异常: status={}, reason={}", status, message);
        return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error(status, message));
    }

    /**
     * 判断当前响应是否为SSE流。
     * SSE流式接口的异常由ChatController内部通过SseEmitter.error()处理，
     * 全局异常处理器不应再写入JSON响应。
     */
    private boolean isSseResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        return contentType != null && contentType.contains("text/event-stream");
    }
}
