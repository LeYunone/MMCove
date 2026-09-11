package com.mmcove.agent.mcp.external;

import com.mmcove.agent.common.model.entity.ProductLine;
import com.mmcove.agent.infra.persistence.repository.ProductLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 产品线名称解析器(MCP 交互全部用名称,不暴露数字 ID)。
 *
 * <p>解析顺序:code 精确 → name 精确 → name 唯一包含(忽略大小写)。
 * 唯一包含支持用户话术里的简称(如"物联"命中"示例产品线");
 * 多个命中视为歧义,要求说全名,避免 AI 传错线。
 *
 * @since 2026-09-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpProductLineResolver {

    private final ProductLineRepository productLineRepository;

    /**
     * 按名称解析产品线。
     *
     * @throws IllegalArgumentException 名称不存在或歧义(中文消息,拦截器/工具层转提示)
     */
    public ProductLine resolve(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("产品线名称不能为空");
        }
        String raw = name.trim();
        List<ProductLine> all = productLineRepository.findAll();

        // 1. code 精确
        for (ProductLine line : all) {
            if (line.getCode().equals(raw)) {
                return line;
            }
        }
        // 2. name 精确
        for (ProductLine line : all) {
            if (line.getName().equals(raw)) {
                return line;
            }
        }
        // 3. 唯一包含(忽略大小写)
        String lower = raw.toLowerCase(Locale.ROOT);
        List<ProductLine> fuzzy = all.stream()
                .filter(l -> l.getName().toLowerCase(Locale.ROOT).contains(lower))
                .toList();
        if (fuzzy.size() == 1) {
            return fuzzy.get(0);
        }
        if (fuzzy.size() > 1) {
            throw new IllegalArgumentException("产品线名称「" + raw + "」歧义,匹配到多个: "
                    + fuzzy.stream().map(l -> l.getName() + "(" + l.getCode() + ")")
                            .reduce((a, b) -> a + "、" + b).orElse(""));
        }
        throw new IllegalArgumentException("产品线不存在: " + raw
                + "(可先调用 listProductLines 查看全部产品线)");
    }
}
