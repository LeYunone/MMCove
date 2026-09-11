package com.mmcove.agent.common.exception;

/**
 * LLM 调用异常。
 */
public class LlmCallException extends BaseException {

    public LlmCallException(String message) {
        super(1001, message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(1001, message, cause);
    }
}
