package com.mmcove.agent.controller;

import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.AiRole;
import com.mmcove.agent.infra.persistence.repository.AiRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI角色管理控制器。
 * 使用 X-Session-Token 认证（isAdminPath）。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-role")
@RequiredArgsConstructor
public class AiRoleController {

    private final AiRoleRepository aiRoleRepository;

    /**
     * 角色列表。
     * GET /api/ai-role/list
     */
    @GetMapping("/list")
    public ApiResponse<List<AiRole>> list() {
        return ApiResponse.success(aiRoleRepository.findAll());
    }

    /**
     * 搜索角色。
     * GET /api/ai-role/search?keyword=xxx
     */
    @GetMapping("/search")
    public ApiResponse<List<AiRole>> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(aiRoleRepository.search(keyword));
    }

    /**
     * 新增角色。
     * POST /api/ai-role
     */
    @PostMapping
    public ApiResponse<AiRole> create(@RequestBody AiRoleRequest request) {
        AiRole role = new AiRole();
        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setContent(request.getContent());
        role.setUserContent(request.getUserContent());
        role.setContentLength(request.getContentLength());
        role.setModel(request.getModel());
        aiRoleRepository.insert(role);
        log.info("[AiRole管理] 新增角色: code={}, name={}", role.getCode(), role.getName());
        return ApiResponse.success(role);
    }

    /**
     * 编辑角色。
     * PUT /api/ai-role
     */
    @PutMapping
    public ApiResponse<AiRole> update(@RequestBody AiRoleUpdateRequest request) {
        AiRole role = aiRoleRepository.findById(request.getId()).orElse(null);
        if (role == null) {
            return ApiResponse.error(404, "角色不存在");
        }
        if (request.getName() != null) {
            role.setName(request.getName());
        }
        if (request.getCode() != null) {
            role.setCode(request.getCode());
        }
        if (request.getContent() != null) {
            role.setContent(request.getContent());
        }
        if (request.getUserContent() != null) {
            role.setUserContent(request.getUserContent());
        }
        if (request.getContentLength() != null) {
            role.setContentLength(request.getContentLength());
        }
        if (request.getModel() != null) {
            role.setModel(request.getModel());
        }
        aiRoleRepository.update(role);
        log.info("[AiRole管理] 更新角色: id={}, code={}", role.getId(), role.getCode());
        return ApiResponse.success(role);
    }

    /**
     * 删除角色。
     * DELETE /api/ai-role/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!aiRoleRepository.delete(id)) {
            return ApiResponse.error(404, "角色不存在");
        }
        log.info("[AiRole管理] 删除角色: id={}", id);
        return ApiResponse.success();
    }

    /**
     * 刷新角色缓存。
     * POST /api/ai-role/refresh-cache
     */
    @PostMapping("/refresh-cache")
    public ApiResponse<Void> refreshCache() {
        aiRoleRepository.refreshCache();
        log.info("[AiRole管理] 刷新角色缓存");
        return ApiResponse.success();
    }

    // ==================== 请求 DTO ====================

    @lombok.Data
    public static class AiRoleRequest {
        private String name;
        private String code;
        private String content;
        private String userContent;
        private Integer contentLength;
        private String model;
    }

    @lombok.Data
    public static class AiRoleUpdateRequest {
        private Long id;
        private String name;
        private String code;
        private String content;
        private String userContent;
        private Integer contentLength;
        private String model;
    }
}
