package com.mmcove.agent.core.agent;

import com.mmcove.agent.common.model.entity.AgentDefinition;
import com.mmcove.agent.infra.persistence.repository.AgentDefinitionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心，启动时从数据库加载 Agent 定义并缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private final AgentDefinitionRepository agentDefinitionRepository;

    private final ConcurrentHashMap<String, AgentDefinition> agentMap = new ConcurrentHashMap<>();
    private volatile String defaultAgentId;

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /**
     * 从数据库刷新缓存。
     */
    public void refreshCache() {
        List<AgentDefinition> definitions = agentDefinitionRepository.findAllActive();
        agentMap.clear();
        defaultAgentId = null;

        for (AgentDefinition def : definitions) {
            agentMap.put(def.getAgentId(), def);
            if (Boolean.TRUE.equals(def.getIsDefault())) {
                defaultAgentId = def.getAgentId();
            }
            log.info("已注册 Agent: {} - {}", def.getAgentId(), def.getName());
        }

        if (defaultAgentId == null && !definitions.isEmpty()) {
            defaultAgentId = definitions.get(0).getAgentId();
        }
        log.info("默认 Agent: {}", defaultAgentId);
    }

    public AgentDefinition getAgent(String agentId) {
        return agentMap.get(agentId);
    }

    public List<AgentDefinition> getAllAgents() {
        return List.copyOf(agentMap.values());
    }

    public AgentDefinition getDefaultAgent() {
        if (defaultAgentId != null) {
            return agentMap.get(defaultAgentId);
        }
        return null;
    }

    public String getDefaultAgentId() {
        return defaultAgentId;
    }
}
