package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.PipelineStageStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流水线阶段序列 Mapper。
 */
@Mapper
public interface PipelineStageStepMapper extends BaseMapper<PipelineStageStep> {
}
