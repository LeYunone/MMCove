package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 Mapper。
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {
}
