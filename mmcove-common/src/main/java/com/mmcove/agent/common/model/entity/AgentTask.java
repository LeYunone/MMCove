package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 异步任务实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTask extends BaseEntity<Long> {

    private String sessionId;
    private String taskType;
    private String status;
    private String params;
    private String result;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime completedAt;
}
