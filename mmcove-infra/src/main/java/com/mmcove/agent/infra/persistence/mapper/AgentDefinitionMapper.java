package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.AgentDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 定义 Mapper。
 */
@Mapper
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinition> {
}
