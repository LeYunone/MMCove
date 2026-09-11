package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.AiRoleChannelGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-渠道-分组关联 Mapper。
 */
@Mapper
public interface AiRoleChannelGroupMapper extends BaseMapper<AiRoleChannelGroup> {
}
