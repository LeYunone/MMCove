package com.mmcove.agent.controller;

import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.AiAbility;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.common.model.entity.AiRoleChannelGroup;
import com.mmcove.agent.infra.persistence.repository.AiAbilityRepository;
import com.mmcove.agent.infra.persistence.repository.AiChannelRepository;
import com.mmcove.agent.infra.persistence.repository.AiRoleChannelGroupRepository;
import com.mmcove.agent.service.DynamicChatModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI渠道管理控制器 + 角色-渠道关联管理。
 * 使用 X-Session-Token 认证（isAdminPath）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AiChannelController {

    private final AiChannelRepository aiChannelRepository;
    private final AiRoleChannelGroupRepository aiRoleChannelGroupRepository;
    private final AiAbilityRepository aiAbilityRepository;
    private final DynamicChatModelFactory dynamicChatModelFactory;

    // ==================== 渠道管理 ====================

    /**
     * 渠道列表。
     * GET /api/ai-channel/list
     */
    @GetMapping("/api/ai-channel/list")
    public ApiResponse<List<AiChannel>> listChannels() {
        return ApiResponse.success(aiChannelRepository.findAll());
    }

    /**
     * 获取所有可用分组（从渠道配置中提取去重后的 groupName）。
     * GET /api/ai-channel/groups
     */
    @GetMapping("/api/ai-channel/groups")
    public ApiResponse<List<String>> listGroups() {
        List<AiChannel> channels = aiChannelRepository.findAll();
        Set<String> groups = new LinkedHashSet<>();
        groups.add("default");
        for (AiChannel ch : channels) {
            if (ch.getGroupName() != null && !ch.getGroupName().isEmpty()) {
                for (String g : ch.getGroupName().split(",")) {
                    g = g.trim();
                    if (!g.isEmpty()) {
                        groups.add(g);
                    }
                }
            }
        }
        return ApiResponse.success(new ArrayList<>(groups));
    }

    /**
     * 新增渠道（含 apiKey/baseUrl，自动同步 ai_ability）。
     * POST /api/ai-channel
     */
    @PostMapping("/api/ai-channel")
    public ApiResponse<AiChannel> createChannel(@RequestBody ChannelRequest request) {
        AiChannel channel = new AiChannel();
        channel.setName(request.getName());
        channel.setModels(request.getModels());
        channel.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        channel.setType(request.getType() != null ? request.getType() : AiChannel.TYPE_OPENAI);
        channel.setApiKey(request.getApiKey());
        channel.setBaseUrl(request.getBaseUrl());
        channel.setWeight(request.getWeight() != null ? request.getWeight() : 10);
        channel.setPriority(request.getPriority() != null ? request.getPriority() : 0L);
        channel.setAutoBan(request.getAutoBan() != null ? request.getAutoBan() : 1);
        channel.setModelMapping(request.getModelMapping());
        channel.setGroupName(request.getGroupName() != null ? request.getGroupName() : "default");
        channel.setRole(request.getRole() != null ? request.getRole() : "");
        aiChannelRepository.insert(channel);

        // 同步 ai_ability
        syncAbilitiesForChannel(channel);

        log.info("[AiChannel管理] 新增渠道: name={}, id={}", channel.getName(), channel.getId());
        return ApiResponse.success(channel);
    }

    /**
     * 编辑渠道。
     * PUT /api/ai-channel
     */
    @PutMapping("/api/ai-channel")
    public ApiResponse<AiChannel> updateChannel(@RequestBody ChannelUpdateRequest request) {
        AiChannel channel = aiChannelRepository.findById(request.getId()).orElse(null);
        if (channel == null) {
            return ApiResponse.error(404, "渠道不存在");
        }
        if (request.getName() != null) {
            channel.setName(request.getName());
        }
        if (request.getModels() != null) {
            channel.setModels(request.getModels());
        }
        if (request.getStatus() != null) {
            channel.setStatus(request.getStatus());
        }
        if (request.getType() != null) {
            channel.setType(request.getType());
        }
        if (request.getApiKey() != null) {
            channel.setApiKey(request.getApiKey());
        }
        if (request.getBaseUrl() != null) {
            channel.setBaseUrl(request.getBaseUrl());
        }
        if (request.getWeight() != null) {
            channel.setWeight(request.getWeight());
        }
        if (request.getPriority() != null) {
            channel.setPriority(request.getPriority());
        }
        if (request.getAutoBan() != null) {
            channel.setAutoBan(request.getAutoBan());
        }
        if (request.getModelMapping() != null) {
            channel.setModelMapping(request.getModelMapping());
        }
        if (request.getGroupName() != null) {
            channel.setGroupName(request.getGroupName());
        }
        if (request.getRole() != null) {
            channel.setRole(request.getRole());
        }
        aiChannelRepository.update(channel);

        // 清除 ChatModel 缓存
        dynamicChatModelFactory.invalidate(channel.getId());

        // 重新同步 ai_ability
        if (request.getModels() != null || request.getStatus() != null) {
            syncAbilitiesForChannel(channel);
        }

        log.info("[AiChannel管理] 更新渠道: id={}", channel.getId());
        return ApiResponse.success(channel);
    }

    /**
     * 删除渠道（自动清理 ai_ability）。
     * DELETE /api/ai-channel/{id}
     */
    @DeleteMapping("/api/ai-channel/{id}")
    public ApiResponse<Void> deleteChannel(@PathVariable Long id) {
        if (!aiChannelRepository.delete(id)) {
            return ApiResponse.error(404, "渠道不存在");
        }

        // 清理 ai_ability
        aiAbilityRepository.deleteByChannelId(id);

        // 清除 ChatModel 缓存
        dynamicChatModelFactory.invalidate(id);

        log.info("[AiChannel管理] 删除渠道: id={}", id);
        return ApiResponse.success();
    }

    /**
     * 查看渠道的能力列表。
     * GET /api/ai-channel/{id}/abilities
     */
    @GetMapping("/api/ai-channel/{id}/abilities")
    public ApiResponse<List<AiAbility>> getChannelAbilities(@PathVariable Long id) {
        return ApiResponse.success(aiAbilityRepository.findByChannelId(id));
    }

    /**
     * 重新同步所有渠道的 abilities 数据。
     * POST /api/ai-channel/sync-abilities
     */
    @PostMapping("/api/ai-channel/sync-abilities")
    public ApiResponse<String> syncAllAbilities() {
        List<AiChannel> channels = aiChannelRepository.findAllEnabled();
        int total = 0;
        for (AiChannel channel : channels) {
            syncAbilitiesForChannel(channel);
            total++;
        }
        log.info("[AiChannel管理] 同步所有渠道能力: count={}", total);
        return ApiResponse.success("已同步 " + total + " 个渠道的能力数据");
    }

    // ==================== 角色-渠道关联管理 ====================

    /**
     * 关联列表。
     * GET /api/ai-role-channel/list
     */
    @GetMapping("/api/ai-role-channel/list")
    public ApiResponse<List<AiRoleChannelGroup>> listRoleChannels() {
        return ApiResponse.success(aiRoleChannelGroupRepository.findAll());
    }

    /**
     * 新增关联。
     * POST /api/ai-role-channel
     */
    @PostMapping("/api/ai-role-channel")
    public ApiResponse<AiRoleChannelGroup> createRoleChannel(@RequestBody RoleChannelRequest request) {
        AiRoleChannelGroup entity = new AiRoleChannelGroup();
        entity.setGroupName(request.getGroupName() != null ? request.getGroupName() : "default");
        entity.setChannelId(request.getChannelId());
        entity.setRoleCode(request.getRoleCode());
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 0L);
        aiRoleChannelGroupRepository.insert(entity);
        log.info("[AiRoleChannel管理] 新增关联: group={}, roleCode={}, channelId={}",
                entity.getGroupName(), entity.getRoleCode(), entity.getChannelId());
        return ApiResponse.success(entity);
    }

    /**
     * 编辑关联。
     * PUT /api/ai-role-channel
     */
    @PutMapping("/api/ai-role-channel")
    public ApiResponse<AiRoleChannelGroup> updateRoleChannel(@RequestBody RoleChannelUpdateRequest request) {
        AiRoleChannelGroup entity = aiRoleChannelGroupRepository.findById(request.getId()).orElse(null);
        if (entity == null) {
            return ApiResponse.error(404, "关联不存在");
        }
        if (request.getGroupName() != null) {
            entity.setGroupName(request.getGroupName());
        }
        if (request.getChannelId() != null) {
            entity.setChannelId(request.getChannelId());
        }
        if (request.getRoleCode() != null) {
            entity.setRoleCode(request.getRoleCode());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
        aiRoleChannelGroupRepository.update(entity);
        log.info("[AiRoleChannel管理] 更新关联: id={}", entity.getId());
        return ApiResponse.success(entity);
    }

    /**
     * 删除关联。
     * DELETE /api/ai-role-channel/{id}
     */
    @DeleteMapping("/api/ai-role-channel/{id}")
    public ApiResponse<Void> deleteRoleChannel(@PathVariable Long id) {
        if (!aiRoleChannelGroupRepository.delete(id)) {
            return ApiResponse.error(404, "关联不存在");
        }
        log.info("[AiRoleChannel管理] 删除关联: id={}", id);
        return ApiResponse.success();
    }

    // ==================== 内部方法 ====================

    /**
     * 同步渠道的 ai_ability 数据。
     */
    private void syncAbilitiesForChannel(AiChannel channel) {
        // 先删除旧的能力数据
        aiAbilityRepository.deleteByChannelId(channel.getId());

        // 如果渠道未启用，不创建新数据
        if (!channel.isEnabled()) {
            return;
        }

        // 解析 models 字段，为每个模型创建能力记录
        String models = channel.getModels();
        if (models == null || models.isEmpty()) {
            return;
        }

        // 支持逗号分隔的多分组（与 new-api 一致）
        String groupStr = channel.getGroupName() != null ? channel.getGroupName() : "default";
        String[] groups = groupStr.split(",");
        String[] modelArr = models.split(",");
        List<AiAbility> abilities = new ArrayList<>();

        for (String group : groups) {
            group = group.trim();
            if (group.isEmpty()) continue;
        for (String model : modelArr) {
            model = model.trim();
            if (!model.isEmpty()) {
                AiAbility ability = new AiAbility();
                ability.setGroupName(group);
                ability.setModel(model);
                ability.setChannelId(channel.getId());
                ability.setEnabled(1);
                ability.setPriority(channel.getPriority() != null ? channel.getPriority() : 0L);
                ability.setWeight(channel.getWeight() != null ? channel.getWeight() : 10);
                abilities.add(ability);
            }
        }
        }

        if (!abilities.isEmpty()) {
            aiAbilityRepository.batchInsert(abilities);
        }

        log.debug("[AiChannel管理] 同步能力: channelId={}, count={}", channel.getId(), abilities.size());
    }

    // ==================== 请求 DTO ====================

    @lombok.Data
    public static class ChannelRequest {
        private String name;
        private String models;
        private Integer status;
        private Integer type;
        private String apiKey;
        private String baseUrl;
        private Integer weight;
        private Long priority;
        private Integer autoBan;
        private String modelMapping;
        private String groupName;
        private String role;
    }

    @lombok.Data
    public static class ChannelUpdateRequest {
        private Long id;
        private String name;
        private String models;
        private Integer status;
        private Integer type;
        private String apiKey;
        private String baseUrl;
        private Integer weight;
        private Long priority;
        private Integer autoBan;
        private String modelMapping;
        private String groupName;
        private String role;
    }

    @lombok.Data
    public static class RoleChannelRequest {
        private String groupName;
        private String roleCode;
        private Long channelId;
        private Long priority;
    }

    @lombok.Data
    public static class RoleChannelUpdateRequest {
        private Long id;
        private String groupName;
        private String roleCode;
        private Long channelId;
        private Long priority;
    }
}