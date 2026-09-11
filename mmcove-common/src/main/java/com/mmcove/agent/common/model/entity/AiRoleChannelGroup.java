package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-渠道-分组关联实体，对应 ai_role_channel_group 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_role_channel_group")
public class AiRoleChannelGroup extends BaseEntity<Long> {

    /** 分组名称 */
    private String groupName;

    /** 渠道ID */
    private Long channelId;

    /** 角色编码 */
    private String roleCode;

    /** 优先级 */
    private Long priority;
}
