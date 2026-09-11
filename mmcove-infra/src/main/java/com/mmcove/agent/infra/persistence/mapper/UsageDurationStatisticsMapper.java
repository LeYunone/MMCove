package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.UsageDurationStatistics;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用时长统计 Mapper。
 */
@Mapper
public interface UsageDurationStatisticsMapper extends BaseMapper<UsageDurationStatistics> {
}
