package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础实体类，包含公共字段。
 * @param <ID> 主键类型，默认 Long（BIGINT AUTO_INCREMENT），ApiUser 使用 String
 */
@Data
public abstract class BaseEntity<ID> {

    /** 主键ID，子类可通过隐藏字段指定具体类型和 @TableId 策略 */
    @TableId(type = IdType.AUTO)
    private ID id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
