package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * API Token 实体，对应 api_token 表。
 * 迁移自 new-api 项目的 Token 模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_token")
public class ApiToken extends BaseEntity<Long> {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID（userId） */
    private String userId;

    /** Token密钥，格式 sk-xxx，唯一索引 */
    private String tokenKey;

    /** 状态: 0=待审核, 1=启用, 2=禁用, 3=过期, 4=额度用尽, 5=已拒绝 */
    private Integer status;

    /** Token名称 */
    private String name;

    /** 创建时间戳 */
    private Long createdTime;

    /** 禁用时间戳 */
    private Long prohibitedTime;

    /** 最后访问时间戳 */
    private Long accessedTime;

    /** 过期时间戳，-1表示永不过期 */
    private Long expiredTime;

    /** 剩余配额 */
    private Integer remainQuota;

    /** 是否无限配额 */
    private Boolean unlimitedQuota;

    /** 是否启用模型限制 */
    private Boolean modelLimitsEnabled;

    /** 允许的模型列表，逗号分隔 */
    private String modelLimits;

    /** 允许的IP地址 */
    private String allowIps;

    /** 已使用配额 */
    private Integer usedQuota;

    /** 分组名称 */
    private String groupName;

    /** 绑定产品线ID(MCP调用方身份;NULL=未绑定) */
    private Long productLineId;

    /** 申请用途说明 */
    private String applyReason;

    /** 审批备注 */
    private String reviewRemark;

    /** 审批人ID（userId） */
    private String reviewedBy;

    /** 审批时间戳 */
    private Long reviewedTime;

    /** 软删除时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime deletedAt;

    /** 设备唯一标识(MAC/SN) */
    private String uniqueKey;

    /** 来源: DEVICE=设备, PC=PC客户端 */
    private String source;

    /** 设备指纹(明文) */
    private String fingerprint;

    /** 当日激活状态: Y=有效, N=无效 */
    private String useVerification;

    /** 验证时间(秒) */
    private Long useVerificationTime;

    /** 设备SN码 */
    private String sn;

    /** 设备MAC地址 */
    private String mac;

    // ==================== 状态常量 ====================

    /** 待审核状态 */
    public static final int STATUS_PENDING = 0;

    /** 启用状态 */
    public static final int STATUS_ENABLED = 1;

    /** 禁用状态 */
    public static final int STATUS_DISABLED = 2;

    /** 过期状态 */
    public static final int STATUS_EXPIRED = 3;

    /** 额度用尽状态 */
    public static final int STATUS_EXHAUSTED = 4;

    /** 已拒绝状态 */
    public static final int STATUS_REJECTED = 5;

    // ==================== 业务方法 ====================

    /**
     * 检查Token是否有效。
     */
    public boolean isValid() {
        return status != null && status == STATUS_ENABLED;
    }

    /**
     * 检查Token是否待审核。
     */
    public boolean isPending() {
        return status != null && status == STATUS_PENDING;
    }

    /**
     * 检查Token是否已被拒绝。
     */
    public boolean isRejected() {
        return status != null && status == STATUS_REJECTED;
    }

    /**
     * 检查Token是否过期。
     */
    public boolean isExpired() {
        if (expiredTime == null || expiredTime == -1) {
            return false;
        }
        return expiredTime < System.currentTimeMillis() / 1000;
    }

    /**
     * 检查配额是否充足。
     */
    public boolean hasQuota() {
        if (unlimitedQuota != null && unlimitedQuota) {
            return true;
        }
        return remainQuota != null && remainQuota > 0;
    }

    /**
     * 获取模型限制Map。
     */
    public java.util.Map<String, Boolean> getModelLimitsMap() {
        java.util.Map<String, Boolean> map = new java.util.HashMap<>();
        if (modelLimits != null && !modelLimits.isEmpty()) {
            for (String model : modelLimits.split(",")) {
                model = model.trim();
                if (!model.isEmpty()) {
                    map.put(model, true);
                }
            }
        }
        return map;
    }

    /**
     * 脱敏Key用于日志输出。
     */
    public String getMaskedKey() {
        if (tokenKey == null || tokenKey.length() < 10) {
            return "***";
        }
        return tokenKey.substring(0, 6) + "***" + tokenKey.substring(tokenKey.length() - 4);
    }
}
