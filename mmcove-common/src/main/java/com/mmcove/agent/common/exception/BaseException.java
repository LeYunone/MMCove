package com.mmcove.agent.common.exception;

import lombok.Getter;

/**
 * 基础异常类，包含错误码和错误信息。
 */
@Getter
public class BaseException extends RuntimeException {

    private final int code;

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
