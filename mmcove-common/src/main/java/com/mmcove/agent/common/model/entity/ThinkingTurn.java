package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 思考轮次实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("thinking_turn")
public class ThinkingTurn extends BaseEntity<Long> {

    private String sessionId;
    private String subTaskId;
    private int turnIndex;
    private String thought;
    private String actionTool;
    /** JSON 格式的工具参数 */
    private String actionArgs;
    private String observation;
    private String status;
}
