package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation")
public class Conversation extends BaseEntity<Long> {

    private String sessionId;
    private Long tenantId;
    private String userId;
    private String agentType;
    private String title;
    private String status;
}
