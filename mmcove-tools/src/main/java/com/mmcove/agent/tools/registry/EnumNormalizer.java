package com.mmcove.agent.tools.registry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 枚举参数归一化器(通用):按 {@link com.mmcove.agent.tools.external.annotation.ParamSpec#enumValues()}
 * 声明,把 LLM 传入的自然语言/变体值,确定性地映射成设备真实接受的枚举标准值。
 *
 * <p>enumValues 格式「标准值:别名1,别名2」(别名为可选)。匹配规则(大小写无关):
 * <ol>
 *   <li>raw 命中某标准值 → 返回该标准值</li>
 *   <li>raw 命中某别名 → 返回该别名对应的标准值</li>
 *   <li>都不命中 → 返回 {@code null}(由调用方决定返回 err 触发 LLM 自愈重试)</li>
 * </ol>
 *
 * <p>「精准防飘移」核心:不硬编码语言表,而是按声明式 enumValues 通用归一——一处逻辑覆盖任意枚举。
 *
 * @since 2026-07-21
 */
public final class EnumNormalizer {

    private EnumNormalizer() {
    }

    /**
     * 按 enumValues 归一化 raw。
     *
     * @param raw         LLM 实际传入的值(可能为自然语言/变体/标准值)
     * @param enumValues  合法枚举声明,格式「标准值:别名1,别名2」
     * @return 命中返回标准值;raw 为空或无法识别返回 {@code null}
     */
    public static String normalize(String raw, String[] enumValues) {
        if (raw == null || raw.isBlank() || enumValues == null || enumValues.length == 0) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        // 先扫一遍:命中标准值立即返回;同时收集别名→标准值映射
        Map<String, String> aliasToStandard = new LinkedHashMap<>();
        for (String entry : enumValues) {
            String[] parts = entry.split(":", 2);
            String standard = parts[0].trim();
            if (!standard.isEmpty() && standard.equalsIgnoreCase(key)) {
                return standard;
            }
            if (parts.length > 1) {
                for (String alias : parts[1].split(",")) {
                    String a = alias.trim();
                    if (!a.isEmpty()) {
                        aliasToStandard.putIfAbsent(a.toLowerCase(Locale.ROOT), standard);
                    }
                }
            }
        }
        return aliasToStandard.get(key);
    }

    /**
     * 拼 enumValues 的合法值提示(供 err 返回,引导 LLM 自愈重填)。
     * 只列标准值(不列别名,保持提示简洁)。
     */
    public static String legalHint(String[] enumValues) {
        if (enumValues == null || enumValues.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String entry : enumValues) {
            String[] parts = entry.split(":", 2);
            String standard = parts[0].trim();
            if (standard.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(standard);
        }
        return sb.toString();
    }
}
