package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.GroupRatioConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分组倍率配置 Mapper。
 */
@Mapper
public interface GroupRatioConfigMapper extends BaseMapper<GroupRatioConfig> {
}
