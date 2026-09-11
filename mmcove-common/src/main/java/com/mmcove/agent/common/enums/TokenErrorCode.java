package com.mmcove.agent.common.enums;

/**
 * Token认证相关错误码。
 */
public enum TokenErrorCode {

    /** Token不存在 */
    TOKEN_NOT_FOUND(401, "Token不存在"),

    /** Token已过期 */
    TOKEN_EXPIRED(401, "Token已过期"),

    /** Token额度已用尽 */
    TOKEN_EXHAUSTED(403, "Token额度已用尽"),

    /** Token状态不可用 */
    TOKEN_DISABLED(403, "Token状态不可用"),

    /** Token待审核 */
    TOKEN_PENDING(403, "Token待审核，请等待管理员审批"),

    /** Token申请已被拒绝 */
    TOKEN_REJECTED(403, "Token申请已被拒绝"),

    /** Token未授权访问此模型 */
    MODEL_NOT_ALLOWED(403, "Token未授权访问此模型"),

    /** 用户已被封禁 */
    USER_DISABLED(403, "用户已被封禁"),

    /** IP不在白名单 */
    IP_NOT_ALLOWED(403, "请求IP不在允许范围内"),

    PERMISSION_DENIED(403,"需要管理员权限"),

    /** 认证失败 */
    AUTH_FAILED(401, "认证失败"),

    /** 每日免费次数已用完 */
    DAILY_LIMIT_EXCEEDED(429, "今日免费次数已用完，请申请 API Token 获取更多配额");

    private final int httpStatus;
    private final String message;

    TokenErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
