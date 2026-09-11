package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ModelPriceConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型计费配置 Mapper。
 */
@Mapper
public interface ModelPriceConfigMapper extends BaseMapper<ModelPriceConfig> {
}
