package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ApiTokenUsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * API Token 使用记录 Mapper。
 */
@Mapper
public interface ApiTokenUsageLogMapper extends BaseMapper<ApiTokenUsageLog> {
}
