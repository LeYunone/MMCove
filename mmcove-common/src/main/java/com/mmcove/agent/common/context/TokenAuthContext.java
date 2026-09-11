package com.mmcove.agent.common.context;

/**
 * Token认证上下文。
 * 使用ThreadLocal存储当前请求的Token认证信息。
 */
public class TokenAuthContext {

    private static final ThreadLocal<TokenAuthContext> CONTEXT = ThreadLocal.withInitial(TokenAuthContext::new);

    /** 当前认证的Token */
    private String tokenKey;

    /** Token对应的用户ID（userId） */
    private String userId;

    /** Token对应的用户名称 */
    private String username;

    /** Token ID */
    private Long tokenId;

    /** Token名称 */
    private String tokenName;

    /** Token是否无限配额 */
    private boolean unlimitedQuota;

    /** Token剩余配额 */
    private Integer remainQuota;

    /** Token已用配额 */
    private Integer usedQuota;

    /** 绑定的产品线ID(MCP 调用方身份;NULL=未绑定,知识库仅可读共享库) */
    private Long productLineId;

    /** Token分组 */
    private String groupName;

    /** 模型限制是否启用 */
    private boolean modelLimitsEnabled;

    /** 允许的模型列表 */
    private String modelLimits;

    private TokenAuthContext() {
    }

    public static TokenAuthContext get() {
        return CONTEXT.get();
    }

    public static void set(TokenAuthContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    // ==================== Getter/Setter ====================

    public String getTokenKey() {
        return tokenKey;
    }

    public void setTokenKey(String tokenKey) {
        this.tokenKey = tokenKey;
    }

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

    public Long getTokenId() {
        return tokenId;
    }

    public void setTokenId(Long tokenId) {
        this.tokenId = tokenId;
    }

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public boolean isUnlimitedQuota() {
        return unlimitedQuota;
    }

    public void setUnlimitedQuota(boolean unlimitedQuota) {
        this.unlimitedQuota = unlimitedQuota;
    }

    public Integer getRemainQuota() {
        return remainQuota;
    }

    public void setRemainQuota(Integer remainQuota) {
        this.remainQuota = remainQuota;
    }

    public Integer getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(Integer usedQuota) {
        this.usedQuota = usedQuota;
    }

    public Long getProductLineId() {
        return productLineId;
    }

    public void setProductLineId(Long productLineId) {
        this.productLineId = productLineId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public boolean isModelLimitsEnabled() {
        return modelLimitsEnabled;
    }

    public void setModelLimitsEnabled(boolean modelLimitsEnabled) {
        this.modelLimitsEnabled = modelLimitsEnabled;
    }

    public String getModelLimits() {
        return modelLimits;
    }

    public void setModelLimits(String modelLimits) {
        this.modelLimits = modelLimits;
    }

    /**
     * 检查是否允许指定模型。
     */
    public boolean isModelAllowed(String model) {
        if (!modelLimitsEnabled || modelLimits == null || modelLimits.isEmpty()) {
            return true;
        }
        String[] allowedModels = modelLimits.split(",");
        for (String allowed : allowedModels) {
            if (allowed.trim().equalsIgnoreCase(model)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 脱敏Token Key用于日志。
     */
    public String getMaskedTokenKey() {
        if (tokenKey == null || tokenKey.length() < 10) {
            return "***";
        }
        return tokenKey.substring(0, 6) + "***" + tokenKey.substring(tokenKey.length() - 4);
    }
}
