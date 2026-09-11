package com.mmcove.agent.common.exception;

/**
 * 工具执行异常。
 */
public class ToolExecutionException extends BaseException {

    public ToolExecutionException(String message) {
        super(1002, message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(1002, message, cause);
    }
}
