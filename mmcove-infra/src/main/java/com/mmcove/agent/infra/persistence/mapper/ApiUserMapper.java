package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ApiUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * API User Mapper。
 */
@Mapper
public interface ApiUserMapper extends BaseMapper<ApiUser> {
}
