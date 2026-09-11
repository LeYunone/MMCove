package com.mmcove.agent.tools.external.model;

import java.time.Instant;
import java.util.Map;

/**
 * 待确认操作模型
 */
public class PendingAction {

    /** 确认令牌 */
    private String confirmationToken;

    /** 系统名称 */
    private String systemName;

    /** 操作名称 */
    private String operationName;

    /** 操作参数（Map或List等任意类型） */
    private Object params;

    /** 操作摘要（用于向用户展示） */
    private String summary;

    /** 认证token（确认执行时使用） */
    private String authToken;

    /** 创建时间 */
    private Instant createdAt;

    /** 过期时间 */
    private Instant expiresAt;

    /** 是否使用query参数模式（true=executeWithQueryParams, false=execute/executeDirect） */
    private boolean queryMode = false;

    public PendingAction() {
    }

    public PendingAction(String confirmationToken, String systemName, String operationName,
                         Object params, String summary, String authToken, int ttlSeconds) {
        this(confirmationToken, systemName, operationName, params, summary, authToken, ttlSeconds, false);
    }

    public PendingAction(String confirmationToken, String systemName, String operationName,
                         Object params, String summary, String authToken, int ttlSeconds, boolean queryMode) {
        this.confirmationToken = confirmationToken;
        this.systemName = systemName;
        this.operationName = operationName;
        this.params = params;
        this.summary = summary;
        this.authToken = authToken;
        this.queryMode = queryMode;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(ttlSeconds);
    }

    /**
     * 判断是否已过期
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public String getConfirmationToken() {
        return confirmationToken;
    }

    public void setConfirmationToken(String confirmationToken) {
        this.confirmationToken = confirmationToken;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public boolean isQueryMode() {
        return queryMode;
    }

    public void setQueryMode(boolean queryMode) {
        this.queryMode = queryMode;
    }
}
