package com.mmcove.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.AgentDefinition;
import com.mmcove.agent.common.model.entity.GroupToolConfig;
import com.mmcove.agent.common.model.entity.ToolDefinition;
import com.mmcove.agent.core.tool.ToolOverrideRegistry;
import com.mmcove.agent.infra.persistence.repository.AgentDefinitionRepository;
import com.mmcove.agent.infra.persistence.repository.GroupToolConfigRepository;
import com.mmcove.agent.infra.persistence.repository.ToolDefinitionRepository;
import com.mmcove.agent.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具定义管理控制器。
 * <p>
 * 提供工具定义的同步、查询、更新、重置等接口，
 * 用于管理后台微调 LLM 看到的工具描述。
 */
@RestController
@RequestMapping("/api/tool-definitions")
@RequiredArgsConstructor
public class ToolDefinitionController {

    private final ToolDefinitionRepository toolDefinitionRepository;
    private final ToolOverrideRegistry toolOverrideRegistry;
    private final ToolRegistry toolRegistry;
    private final GroupToolConfigRepository groupToolConfigRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;

    /**
     * 同步已注册的 @Tool 方法到数据库。
     * 首次使用或新增工具后调用。
     */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> syncToolDefinitions() {
        List<ToolCallback> callbacks = toolRegistry.getOriginalToolCallbacks();
        Map<String, String> sourceClasses = toolRegistry.getToolSourceClasses();
        Set<String> dangerousToolNames = toolRegistry.getOriginallyDangerousToolNames();
        int inserted = 0;
        int updated = 0;

        for (ToolCallback callback : callbacks) {
            String toolName = callback.getToolDefinition().name();
            String description = callback.getToolDefinition().description();
            String inputSchema = callback.getToolDefinition().inputSchema();
            String sourceClass = sourceClasses.getOrDefault(toolName, "Unknown");
            boolean originallyDangerous = dangerousToolNames.contains(toolName);

            var existing = toolDefinitionRepository.findByToolName(toolName);
            if (existing.isPresent()) {
                // 已存在则更新原始描述和危险标记（保留自定义描述）
                ToolDefinition def = existing.get();
                def.setOriginalDescription(description);
                def.setSourceClass(sourceClass);
                def.setDangerous(originallyDangerous);
                if (def.getInputSchema() == null) {
                    def.setInputSchema(inputSchema);
                }
                toolDefinitionRepository.update(def);
                updated++;
            } else {
                // 不存在则新增，原始描述和当前描述相同
                ToolDefinition def = new ToolDefinition();
                def.setToolName(toolName);
                def.setDescription(description);
                def.setOriginalDescription(description);
                def.setInputSchema(inputSchema);
                def.setDangerous(originallyDangerous);
                def.setStatus("ACTIVE");
                def.setSourceClass(sourceClass);
                toolDefinitionRepository.insert(def);
                inserted++;
            }
        }

        toolOverrideRegistry.refreshCache();
        return ApiResponse.success(Map.of("inserted", inserted, "updated", updated, "total", callbacks.size()));
    }

    /**
     * 分页查询工具定义。
     */
    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> pageToolDefinitions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String sourceClass,
            @RequestParam(required = false) String keyword) {
        Page<ToolDefinition> page = toolDefinitionRepository.findPage(pageNum, pageSize, sourceClass, keyword);
        return ApiResponse.success(Map.of(
                "content", page.getRecords(),
                "totalElements", page.getTotal(),
                "totalPages", page.getPages(),
                "pageNumber", page.getCurrent(),
                "pageSize", page.getSize()
        ));
    }

    /**
     * 根据ID查询工具定义详情。
     */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<ToolDefinition> getToolDefinition(@PathVariable Long id) {
        return toolDefinitionRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "工具定义不存在"));
    }

    /**
     * 更新工具定义（保存后自动刷新缓存）。
     */
    @PutMapping("/{id:\\d+}")
    public ApiResponse<ToolDefinition> updateToolDefinition(@PathVariable Long id,
                                                             @RequestBody ToolDefinition toolDefinition) {
        return toolDefinitionRepository.findById(id)
                .map(existing -> {
                    toolDefinition.setId(id);
                    toolDefinition.setCreatedAt(existing.getCreatedAt());
                    toolDefinition.setToolName(existing.getToolName());
                    toolDefinition.setOriginalDescription(existing.getOriginalDescription());
                    toolDefinition.setSourceClass(existing.getSourceClass());
                    toolDefinitionRepository.update(toolDefinition);
                    toolOverrideRegistry.refreshCache();
                    return ApiResponse.success(toolDefinition);
                })
                .orElse(ApiResponse.error(404, "工具定义不存在"));
    }

    /**
     * 重置为原始描述。
     */
    @PostMapping("/{id:\\d+}/reset")
    public ApiResponse<ToolDefinition> resetToolDefinition(@PathVariable Long id) {
        return toolDefinitionRepository.findById(id)
                .map(existing -> {
                    existing.setDescription(existing.getOriginalDescription());
                    existing.setDangerous(false);
                    existing.setInputSchema(null);
                    toolDefinitionRepository.update(existing);
                    toolOverrideRegistry.refreshCache();
                    return ApiResponse.success(existing);
                })
                .orElse(ApiResponse.error(404, "工具定义不存在"));
    }

    /**
     * 获取所有来源类列表（筛选用）。
     */
    @GetMapping("/source-classes")
    public ApiResponse<List<String>> getSourceClasses() {
        List<String> classes = toolDefinitionRepository.findDistinctSourceClasses();
        return ApiResponse.success(classes);
    }

    /**
     * 手动刷新缓存。
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refreshCache() {
        toolOverrideRegistry.refreshCache();
        return ApiResponse.success();
    }

    // ==================== 分组工具配置 ====================

    /**
     * 查询分组已配置的工具。
     */
    @GetMapping("/group/{groupName}")
    public ApiResponse<List<GroupToolConfig>> getGroupTools(@PathVariable String groupName) {
        List<GroupToolConfig> configs = groupToolConfigRepository.findByGroup(groupName);
        return ApiResponse.success(configs);
    }

    /**
     * 设置分组的工具列表（全量替换）。
     */
    @PutMapping("/group/{groupName}")
    public ApiResponse<Void> setGroupTools(@PathVariable String groupName,
                                            @RequestBody Map<String, List<String>> body) {
        List<String> toolNames = body.get("toolNames");
        if (toolNames == null) {
            return ApiResponse.error(400, "toolNames 不能为空");
        }
        groupToolConfigRepository.replaceAll(groupName, toolNames);
        return ApiResponse.success();
    }

    /**
     * 查询可分配的工具列表（所有已注册的工具）。
     */
    @GetMapping("/group/{groupName}/available")
    public ApiResponse<List<Map<String, Object>>> getAvailableTools(
            @PathVariable String groupName,
            @RequestParam(required = false) String sourceClass) {
        Map<String, String> sourceClassMap = toolRegistry.getToolSourceClasses();
        List<ToolCallback> allCallbacks = toolRegistry.getOriginalToolCallbacks();
        List<String> enabledTools = groupToolConfigRepository.findEnabledToolNamesByGroup(groupName);
        Set<String> enabledSet = new java.util.HashSet<>(enabledTools);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolCallback callback : allCallbacks) {
            String toolName = callback.getToolDefinition().name();
            String srcClass = sourceClassMap.getOrDefault(toolName, "");

            if (sourceClass != null && !sourceClass.isEmpty() && !sourceClass.equals(srcClass)) {
                continue;
            }

            String desc = callback.getToolDefinition().description();
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("name", toolName);
            item.put("description", desc != null ? desc : "");
            item.put("sourceClass", srcClass);
            item.put("enabled", enabledSet.contains(toolName));
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    /**
     * 列出所有工具分组(聚合:工具数 / 危险工具数 / 绑定的 Agent)。
     * <p>「工具分组可视化管理」:分组 = group_tool_config 里 distinct 的 group_name(不建实体表)。
     * 前端分组列表页用。
     */
    @GetMapping("/groups")
    public ApiResponse<List<Map<String, Object>>> listGroups() {
        List<String> groupNames = groupToolConfigRepository.findAllGroupNames();
        Set<String> dangerousToolNames = toolRegistry.getOriginallyDangerousToolNames();

        // 绑定 Agent 聚合:tool_group_name → agentId 列表
        java.util.Map<String, List<String>> groupToAgents = new java.util.HashMap<>();
        for (AgentDefinition a : agentDefinitionRepository.findAllActive()) {
            String g = a.getToolGroupName();
            if (g != null && !g.isBlank()) {
                groupToAgents.computeIfAbsent(g, k -> new ArrayList<>()).add(a.getAgentId());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String g : groupNames) {
            List<String> tools = groupToolConfigRepository.findEnabledToolNamesByGroup(g);
            long dangerousCount = tools.stream().filter(dangerousToolNames::contains).count();
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("groupName", g);
            item.put("toolCount", tools.size());
            item.put("dangerousCount", dangerousCount);
            item.put("boundAgents", groupToAgents.getOrDefault(g, List.of()));
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    /**
     * 删除分组(清空该组工具配置)。
     * <p>注意:绑定该组的 Agent 的 toolGroupName 不会被自动清空(保留引用,运营自行解绑);
     * 因 Agent 路由时该组为空会回退挂全量(兜底),不影响运行。
     */
    @DeleteMapping("/group/{groupName}")
    public ApiResponse<Void> deleteGroup(@PathVariable String groupName) {
        groupToolConfigRepository.deleteByGroup(groupName);
        return ApiResponse.success();
    }
}
