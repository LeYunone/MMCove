package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.PipelineStage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流水线阶段 Mapper。
 */
@Mapper
public interface PipelineStageMapper extends BaseMapper<PipelineStage> {
}
