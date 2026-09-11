package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务 Mapper。
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
