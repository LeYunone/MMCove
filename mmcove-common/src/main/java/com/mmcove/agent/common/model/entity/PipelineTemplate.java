package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流水线模板实体,对应 pipeline_template 表。
 *
 * <p>{@code intentType} 唯一约束 = 一类意图仅一条流水线,意图分类结果直接索引到流水线,
 * 消灭"同意图多模板"的选择歧义。{@code keywords} 供意图分类关键词前置匹配
 * (对标 {@code knowledge_base.keywords} 先例)。
 *
 * @since 2026-09-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pipeline_template")
public class PipelineTemplate extends BaseEntity<Long> {

    /** 流水线编码(如 bug-fix) */
    private String pipelineCode;

    /** 流水线名称(如 缺陷修复流水线) */
    private String name;

    /** 意图类型: BUG_FIX/FEATURE_DEV/REFACTOR/TEST_ENHANCE(同 scope 内应用层保唯一) */
    private String intentType;

    /** 归属产品线(NULL=全局共享模板;非 NULL=该线私有副本,compose 选线私有优先) */
    private Long productLineId;

    /** 流水线说明(LLM 意图分类候选描述) */
    private String description;

    /** 意图关键词,逗号分隔(关键词前置匹配) */
    private String keywords;

    /** 执行约定段(追加在主提示词尾部;空则用服务端内置默认约定) */
    private String executionConvention;

    /** 状态:1启用 0停用 */
    private Integer status;
}
