package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ApiToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * API Token Mapper。
 */
@Mapper
public interface ApiTokenMapper extends BaseMapper<ApiToken> {
}
