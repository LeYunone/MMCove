package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.GroupToolConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分组工具配置 Mapper。
 */
@Mapper
public interface GroupToolConfigMapper extends BaseMapper<GroupToolConfig> {
}
