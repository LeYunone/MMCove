package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI角色定义实体，对应 ai_role 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_role")
public class AiRole extends BaseEntity<Long> {

    /** 角色名称 */
    private String name;

    /** 角色编码（如 def_translator） */
    private String code;

    /** 系统 prompt 模板（含 {{key}} 占位符） */
    private String content;

    /** 用户内容模板 */
    private String userContent;

    /** 内容固定长度 */
    private Integer contentLength;

    /** 默认模型 */
    private String model;
}
