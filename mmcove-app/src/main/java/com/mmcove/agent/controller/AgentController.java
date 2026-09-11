package com.mmcove.agent.controller;

import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.AgentDefinition;
import com.mmcove.agent.core.agent.AgentRegistry;
import com.mmcove.agent.infra.persistence.repository.AgentDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 管理控制器。
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistry agentRegistry;
    private final AgentDefinitionRepository agentDefinitionRepository;

    /**
     * 列出所有 Agent 定义（缓存中的活跃 Agent）。
     */
    @GetMapping
    public ApiResponse<List<AgentDefinition>> listAgents() {
        return ApiResponse.success(agentRegistry.getAllAgents());
    }

    /**
     * 获取单个 Agent（包含禁用状态，从数据库查询）。
     */
    @GetMapping("/{agentId}")
    public ApiResponse<AgentDefinition> getAgent(@PathVariable String agentId) {
        return agentDefinitionRepository.findByAgentId(agentId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "Agent 不存在"));
    }

    /**
     * 新增 Agent。
     */
    @PostMapping
    public ApiResponse<AgentDefinition> createAgent(@RequestBody AgentDefinition agent) {
        agentDefinitionRepository.insert(agent);
        agentRegistry.refreshCache();
        return ApiResponse.success(agent);
    }

    /**
     * 更新 Agent。
     */
    @PutMapping("/{agentId}")
    public ApiResponse<AgentDefinition> updateAgent(@PathVariable String agentId,
                                                     @RequestBody AgentDefinition agent) {
        return agentDefinitionRepository.findByAgentId(agentId)
                .map(existing -> {
                    agent.setId(existing.getId());
                    agent.setCreatedAt(existing.getCreatedAt());
                    agent.setAgentId(agentId);
                    agentDefinitionRepository.update(agent);
                    agentRegistry.refreshCache();
                    return ApiResponse.success(agent);
                })
                .orElse(ApiResponse.error(404, "Agent 不存在"));
    }

    /**
     * 切换 Agent 启用/禁用状态。
     */
    @PutMapping("/{agentId}/status")
    public ApiResponse<Void> toggleStatus(@PathVariable String agentId,
                                           @RequestBody Map<String, String> body) {
        return agentDefinitionRepository.findByAgentId(agentId)
                .map(existing -> {
                    existing.setStatus(body.getOrDefault("status", "ACTIVE"));
                    agentDefinitionRepository.update(existing);
                    agentRegistry.refreshCache();
                    return ApiResponse.<Void>success();
                })
                .orElse(ApiResponse.error(404, "Agent 不存在"));
    }

    /**
     * 手动刷新缓存。
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refreshCache() {
        agentRegistry.refreshCache();
        return ApiResponse.success();
    }
}
