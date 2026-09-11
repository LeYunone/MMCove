package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ThinkingTurn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 思考轮次 Mapper。
 */
@Mapper
public interface ThinkingTurnMapper extends BaseMapper<ThinkingTurn> {
}
