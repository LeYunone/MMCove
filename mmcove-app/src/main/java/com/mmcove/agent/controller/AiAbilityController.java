package com.mmcove.agent.controller;

import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.AiAbility;
import com.mmcove.agent.infra.persistence.repository.AiAbilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型能力映射管理接口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AiAbilityController {

    private final AiAbilityRepository aiAbilityRepository;

    /**
     * 查询可用模型列表。
     * GET /api/ai-ability/models
     */
    @GetMapping("/api/ai-ability/models")
    public ApiResponse<List<String>> listModels() {
        return ApiResponse.success(aiAbilityRepository.findAllModelNames());
    }

    /**
     * 查询分组下的所有能力。
     * GET /api/ai-ability/group/{group}
     */
    @GetMapping("/api/ai-ability/group/{group}")
    public ApiResponse<List<AiAbility>> listByGroup(@PathVariable String group) {
        return ApiResponse.success(aiAbilityRepository.findByGroup(group));
    }

    /**
     * 手动添加能力映射。
     * POST /api/ai-ability
     */
    @PostMapping("/api/ai-ability")
    public ApiResponse<AiAbility> createAbility(@RequestBody AbilityRequest request) {
        AiAbility ability = new AiAbility();
        ability.setGroupName(request.getGroupName() != null ? request.getGroupName() : "default");
        ability.setModel(request.getModel());
        ability.setChannelId(request.getChannelId());
        ability.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        ability.setPriority(request.getPriority() != null ? request.getPriority() : 0L);
        ability.setWeight(request.getWeight() != null ? request.getWeight() : 10);
        aiAbilityRepository.insert(ability);
        log.info("[AiAbility管理] 新增能力: group={}, model={}, channelId={}",
                ability.getGroupName(), ability.getModel(), ability.getChannelId());
        return ApiResponse.success(ability);
    }

    /**
     * 删除能力映射。
     * DELETE /api/ai-ability/{id}
     */
    @DeleteMapping("/api/ai-ability/{id}")
    public ApiResponse<Void> deleteAbility(@PathVariable Long id) {
        if (!aiAbilityRepository.delete(id)) {
            return ApiResponse.error(404, "能力映射不存在");
        }
        log.info("[AiAbility管理] 删除能力: id={}", id);
        return ApiResponse.success();
    }

    // ==================== 请求 DTO ====================

    @lombok.Data
    public static class AbilityRequest {
        private String groupName;
        private String model;
        private Long channelId;
        private Integer enabled;
        private Long priority;
        private Integer weight;
    }
}
