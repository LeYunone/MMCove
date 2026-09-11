package com.mmcove.agent.controller;

import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.ProductLine;
import com.mmcove.agent.infra.persistence.repository.ProductLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产品线管理控制器(知识库产品线体系顶层维度)。
 *
 * <p>产品线 → 知识库两级分类;各线知识库默认隔离,共享库(is_shared=1)可跨线读。
 * {@code code} 参与 Milvus collection 命名防跨线同名冲突,创建后不可改。
 *
 * @since 2026-09-07
 */
@RestController
@RequestMapping("/api/product-lines")
@RequiredArgsConstructor
public class ProductLineController {

    private final ProductLineRepository productLineRepository;

    /** 全量列表(管理端目录) */
    @GetMapping
    public ApiResponse<List<ProductLine>> list() {
        return ApiResponse.success(productLineRepository.findAll());
    }

    /** 创建产品线(code 全局唯一;code 参与集合命名,创建后不可改) */
    @PostMapping
    public ApiResponse<ProductLine> create(@RequestBody ProductLine line) {
        if (!StringUtils.hasText(line.getCode())) {
            return ApiResponse.error(400, "产品线编码(code)不能为空");
        }
        if (!StringUtils.hasText(line.getName())) {
            return ApiResponse.error(400, "产品线名称(name)不能为空");
        }
        String code = line.getCode().trim();
        if (!code.matches("[a-zA-Z0-9_-]+")) {
            return ApiResponse.error(400, "产品线编码仅允许字母/数字/下划线/中划线(用于 collection 命名)");
        }
        if (productLineRepository.findByCode(code).isPresent()) {
            return ApiResponse.error(400, "产品线编码已存在: " + code);
        }
        line.setCode(code);
        line.setName(line.getName().trim());
        line.setStatus(1);
        productLineRepository.insert(line);
        return ApiResponse.success(line);
    }

    /** 更新产品线(name/description/status;code 被 collection 名引用,不可改) */
    @PutMapping("/{id:\\d+}")
    public ApiResponse<ProductLine> update(@PathVariable Long id, @RequestBody ProductLine line) {
        return productLineRepository.findById(id)
                .map(existing -> {
                    line.setId(id);
                    line.setCode(existing.getCode());
                    line.setCreatedAt(existing.getCreatedAt());
                    if (line.getStatus() == null) {
                        line.setStatus(existing.getStatus());
                    }
                    productLineRepository.update(line);
                    return ApiResponse.success(line);
                })
                .orElse(ApiResponse.error(404, "产品线不存在"));
    }

    /** 删除产品线(有挂载知识库时拒绝,需先删除或迁移其下知识库) */
    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (productLineRepository.findById(id).isEmpty()) {
            return ApiResponse.error(404, "产品线不存在");
        }
        long kbCount = productLineRepository.countKnowledgeBases(id);
        if (kbCount > 0) {
            return ApiResponse.error(400, "该产品线下还有 " + kbCount + " 个知识库,请先删除或迁移");
        }
        productLineRepository.deleteById(id);
        return ApiResponse.success();
    }
}
