package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 已解析实体记忆(L3 实体感知记忆)。
 *
 * <p>跨轮保存 {@code resolveDeviceOrGroup} 解析出的 deviceKey/groupId,
 * 解决 v1 简单窗口导致的「它/这台」跨轮指代丢失问题(评审 ISSUE-003)。
 *
 * @since 2026-07-21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resolved_entity")
public class ResolvedEntity extends BaseEntity<Long> {

    /** 会话 ID */
    private String sessionId;

    /** 实体类型:device / group */
    private String entityType;

    /** 实体 Key:deviceKey(TEID_xxx)/ groupId */
    private String entityKey;

    /** 用户原话/设备名(跨轮指代匹配用,如「会议室大屏」) */
    private String displayName;

    /** 解析来源工具(如 resolveDeviceOrGroup) */
    private String sourceTool;

    /** 过期时间(超过则不再注入,需重新解析;由 entity.expire-minutes 配置) */
    private LocalDateTime expiresAt;
}
