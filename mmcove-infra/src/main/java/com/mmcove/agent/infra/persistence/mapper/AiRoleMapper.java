package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.AiRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI角色 Mapper。
 */
@Mapper
public interface AiRoleMapper extends BaseMapper<AiRole> {
}
