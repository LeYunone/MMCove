package com.mmcove.agent.common.exception;

import com.mmcove.agent.common.enums.TokenErrorCode;
import lombok.Getter;

/**
 * Token认证异常。
 */
@Getter
public class TokenAuthException extends RuntimeException {

    private final TokenErrorCode errorCode;

    public TokenAuthException(TokenErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public TokenAuthException(TokenErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
