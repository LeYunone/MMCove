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
 * 用户实体，对应 api_user 表（自建用户体系：注册/登录的本地账户，同时承载 API Token 配额）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_user")
public class ApiUser extends BaseEntity<String> {

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt加密存储） */
    private String password;

    /** 角色: 1=普通用户, 10=管理员, 100=超级管理员 */
    private Integer role;

    /** 状态: 1=启用, 2=禁用 */
    private Integer status;

    /** 用户配额 */
    private Long quota;

    /** 访问令牌 */
    private String accessToken;

    /** 软删除时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime deletedAt;

    // ==================== 角色常量 ====================

    /** 普通用户 */
    public static final int ROLE_COMMON_USER = 1;

    /** 管理员 */
    public static final int ROLE_ADMIN_USER = 10;

    /** 超级管理员 */
    public static final int ROLE_ROOT_USER = 100;

    // ==================== 状态常量 ====================

    /** 启用状态 */
    public static final int STATUS_ENABLED = 1;

    /** 禁用状态 */
    public static final int STATUS_DISABLED = 2;

    // ==================== 业务方法 ====================

    /**
     * 检查是否为管理员及以上角色。
     */
    public boolean isAdmin() {
        return role != null && role >= ROLE_ADMIN_USER;
    }

    /**
     * 检查是否为超级管理员。
     */
    public boolean isRoot() {
        return role != null && role >= ROLE_ROOT_USER;
    }

    /**
     * 检查用户是否启用。
     */
    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLED;
    }
}
