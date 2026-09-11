package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 响应格式模板实体，用于规范 LLM 输出格式。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("response_template")
public class ResponseTemplate extends BaseEntity<Long> {

    /** 模板编码，全局唯一 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 作用域：SCENE（通用场景）/ TOOL（工具结果） */
    private String scope;

    /** 场景类型（SCENE 时有效） */
    private String sceneType;

    /** 工具方法名（TOOL 时有效） */
    private String toolName;

    /** 绑定 Agent ID，空表示全局 */
    private String agentId;

    /** 模板提示词内容 */
    private String templateContent;

    /** 优先级，越大越优先 */
    private Integer priority;

    /** 状态：ACTIVE / INACTIVE */
    private String status;
}
