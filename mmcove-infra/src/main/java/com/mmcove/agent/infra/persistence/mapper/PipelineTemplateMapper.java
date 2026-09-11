package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.PipelineTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流水线模板 Mapper。
 */
@Mapper
public interface PipelineTemplateMapper extends BaseMapper<PipelineTemplate> {
}
