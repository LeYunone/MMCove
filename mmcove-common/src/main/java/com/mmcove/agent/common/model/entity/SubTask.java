package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 子任务实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sub_task")
public class SubTask extends BaseEntity<Long> {

    private String sessionId;
    private String taskPlanId;
    private int taskIndex;
    private String title;
    private String status;
    private boolean parallel;
    private String result;
    private String errorMessage;
}
