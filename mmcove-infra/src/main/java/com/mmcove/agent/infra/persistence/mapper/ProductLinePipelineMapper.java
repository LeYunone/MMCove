package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ProductLinePipeline;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品线↔流水线 绑定 Mapper。
 */
@Mapper
public interface ProductLinePipelineMapper extends BaseMapper<ProductLinePipeline> {
}
