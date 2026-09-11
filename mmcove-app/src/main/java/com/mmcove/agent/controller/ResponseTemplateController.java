package com.mmcove.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.ResponseTemplate;
import com.mmcove.agent.core.template.ResponseTemplateRegistry;
import com.mmcove.agent.infra.persistence.repository.ResponseTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 响应格式模板管理控制器。
 */
@RestController
@RequestMapping("/api/response-templates")
@RequiredArgsConstructor
public class ResponseTemplateController {

    private final ResponseTemplateRepository responseTemplateRepository;
    private final ResponseTemplateRegistry responseTemplateRegistry;

    /**
     * 分页查询模板（管理后台用）。
     */
    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> pageTemplates(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String keyword) {
        Page<ResponseTemplate> page = responseTemplateRepository.findPage(pageNum, pageSize, scope, keyword);
        return ApiResponse.success(Map.of(
                "content", page.getRecords(),
                "totalElements", page.getTotal(),
                "totalPages", page.getPages(),
                "pageNumber", page.getCurrent(),
                "pageSize", page.getSize()
        ));
    }

    /**
     * 列出所有模板。
     */
    @GetMapping
    public ApiResponse<List<ResponseTemplate>> listTemplates() {
        return ApiResponse.success(responseTemplateRegistry.getAllTemplates());
    }

    /**
     * 根据 ID 查询模板。
     */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<ResponseTemplate> getTemplate(@PathVariable Long id) {
        return responseTemplateRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "模板不存在"));
    }

    /**
     * 新增模板。
     */
    @PostMapping
    public ApiResponse<ResponseTemplate> createTemplate(@RequestBody ResponseTemplate template) {
        responseTemplateRepository.insert(template);
        responseTemplateRegistry.refreshCache();
        return ApiResponse.success(template);
    }

    /**
     * 更新模板。
     */
    @PutMapping("/{id:\\d+}")
    public ApiResponse<ResponseTemplate> updateTemplate(@PathVariable Long id,
                                                         @RequestBody ResponseTemplate template) {
        return responseTemplateRepository.findById(id)
                .map(existing -> {
                    template.setId(id);
                    template.setCreatedAt(existing.getCreatedAt());
                    responseTemplateRepository.update(template);
                    responseTemplateRegistry.refreshCache();
                    return ApiResponse.success(template);
                })
                .orElse(ApiResponse.error(404, "模板不存在"));
    }

    /**
     * 删除模板。
     */
    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        responseTemplateRepository.deleteById(id);
        responseTemplateRegistry.refreshCache();
        return ApiResponse.success();
    }

    /**
     * 手动刷新缓存。
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refreshCache() {
        responseTemplateRegistry.refreshCache();
        return ApiResponse.success();
    }
}
