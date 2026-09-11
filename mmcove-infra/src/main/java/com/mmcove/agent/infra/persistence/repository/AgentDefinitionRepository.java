package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.AgentDefinition;
import com.mmcove.agent.infra.persistence.mapper.AgentDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 定义增删改查仓库。
 */
@Repository
@RequiredArgsConstructor
public class AgentDefinitionRepository {

    private final AgentDefinitionMapper agentDefinitionMapper;

    /**
     * 查询所有活跃的 Agent 定义。
     */
    public List<AgentDefinition> findAllActive() {
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentDefinition::getStatus, "ACTIVE")
                .orderByAsc(AgentDefinition::getId);
        return agentDefinitionMapper.selectList(wrapper);
    }

    /**
     * 根据 agentId 查询。
     */
    public Optional<AgentDefinition> findByAgentId(String agentId) {
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentDefinition::getAgentId, agentId);
        return Optional.ofNullable(agentDefinitionMapper.selectOne(wrapper));
    }

    /**
     * 查询所有 Agent 定义（含禁用）。
     */
    public List<AgentDefinition> findAll() {
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AgentDefinition::getId);
        return agentDefinitionMapper.selectList(wrapper);
    }

    /**
     * 查询默认 Agent。
     */
    public Optional<AgentDefinition> findDefault() {
        LambdaQueryWrapper<AgentDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentDefinition::getIsDefault, true)
                .eq(AgentDefinition::getStatus, "ACTIVE");
        return Optional.ofNullable(agentDefinitionMapper.selectOne(wrapper));
    }

    /**
     * 新增 Agent 定义。
     */
    public AgentDefinition insert(AgentDefinition agent) {
        agentDefinitionMapper.insert(agent);
        return agent;
    }

    /**
     * 更新 Agent 定义。
     */
    public AgentDefinition update(AgentDefinition agent) {
        agentDefinitionMapper.updateById(agent);
        return agent;
    }
}
