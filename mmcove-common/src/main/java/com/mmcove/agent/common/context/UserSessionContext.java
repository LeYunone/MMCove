package com.mmcove.agent.common.context;

/**
 * 用户会话上下文。
 * 使用ThreadLocal存储当前登录用户的信息。
 */
public class UserSessionContext {

    private static final ThreadLocal<UserSessionContext> CONTEXT = ThreadLocal.withInitial(UserSessionContext::new);

    /** 用户ID（自建用户体系主键） */
    private String userId;

    /** 用户名 */
    private String username;

    /** 角色 */
    private Integer role;

    /** 访问令牌 */
    private String accessToken;

    private UserSessionContext() {
    }

    public static UserSessionContext get() {
        return CONTEXT.get();
    }

    public static void set(UserSessionContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    // ==================== Getter/Setter ====================

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * 检查是否为管理员。
     */
    public boolean isAdmin() {
        return role != null && role >= 10;
    }

    /**
     * 检查是否为超级管理员。
     */
    public boolean isRoot() {
        return role != null && role >= 100;
    }
}
