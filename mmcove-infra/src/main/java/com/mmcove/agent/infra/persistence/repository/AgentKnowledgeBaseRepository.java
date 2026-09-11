package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.AgentKnowledgeBase;
import com.mmcove.agent.infra.persistence.mapper.AgentKnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent ↔ 知识库 关联仓库。
 *
 * <p>知识库场景路由({@code KnowledgeBaseRouter})的候选集来源:某 Agent 启用的知识库列表。
 * 隔离维度为 agentId(项目多租户未启用,不依赖 TenantContext)。
 * 全量替换(replaceAll) 仿 {@link GroupToolConfigRepository#replaceAll},供前端"关联知识库"多选保存。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentKnowledgeBaseRepository {

    private final AgentKnowledgeBaseMapper agentKnowledgeBaseMapper;

    /** 选库候选集:某 Agent 启用的知识库 id 列表 */
    public List<Long> findEnabledKbIdsByAgent(String agentId) {
        LambdaQueryWrapper<AgentKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledgeBase::getAgentId, agentId)
                .eq(AgentKnowledgeBase::getEnabled, 1)
                .select(AgentKnowledgeBase::getKbId);
        return agentKnowledgeBaseMapper.selectList(wrapper).stream()
                .map(AgentKnowledgeBase::getKbId)
                .toList();
    }

    /** 管理用:某 Agent 全部关联(含禁用) */
    public List<AgentKnowledgeBase> findAllByAgent(String agentId) {
        LambdaQueryWrapper<AgentKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledgeBase::getAgentId, agentId);
        return agentKnowledgeBaseMapper.selectList(wrapper);
    }

    public void bind(String agentId, Long kbId) {
        AgentKnowledgeBase rel = new AgentKnowledgeBase();
        rel.setAgentId(agentId);
        rel.setKbId(kbId);
        rel.setEnabled(1);
        agentKnowledgeBaseMapper.insert(rel);
    }

    public boolean unbind(String agentId, Long kbId) {
        LambdaQueryWrapper<AgentKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledgeBase::getAgentId, agentId)
                .eq(AgentKnowledgeBase::getKbId, kbId);
        return agentKnowledgeBaseMapper.delete(wrapper) > 0;
    }

    /** 全量替换某 Agent 的关联知识库(先删后插,供前端多选保存) */
    public void replaceAll(String agentId, List<Long> kbIds) {
        LambdaQueryWrapper<AgentKnowledgeBase> del = new LambdaQueryWrapper<>();
        del.eq(AgentKnowledgeBase::getAgentId, agentId);
        agentKnowledgeBaseMapper.delete(del);

        if (kbIds == null || kbIds.isEmpty()) {
            return;
        }
        for (Long kbId : kbIds) {
            AgentKnowledgeBase rel = new AgentKnowledgeBase();
            rel.setAgentId(agentId);
            rel.setKbId(kbId);
            rel.setEnabled(1);
            agentKnowledgeBaseMapper.insert(rel);
        }
    }
}
