package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ProductLine;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品线 Mapper。
 */
@Mapper
public interface ProductLineMapper extends BaseMapper<ProductLine> {
}
