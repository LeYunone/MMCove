package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.TokenUsageQuantity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Token 用量统计 Mapper。
 */
@Mapper
public interface TokenUsageQuantityMapper extends BaseMapper<TokenUsageQuantity> {
}
