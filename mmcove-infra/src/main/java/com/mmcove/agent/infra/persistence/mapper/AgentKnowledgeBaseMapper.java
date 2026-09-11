package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.AgentKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent ↔ 知识库 关联 Mapper。
 */
@Mapper
public interface AgentKnowledgeBaseMapper extends BaseMapper<AgentKnowledgeBase> {
}
