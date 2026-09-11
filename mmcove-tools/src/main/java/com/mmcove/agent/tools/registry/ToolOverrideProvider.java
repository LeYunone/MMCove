package com.mmcove.agent.tools.registry;

import java.util.Optional;

/**
 * 工具覆盖配置提供者接口。
 * <p>
 * mmcove-tools 模块通过此接口获取工具描述覆盖配置，
 * 具体实现由 mmcove-core 模块提供，避免 tools 模块依赖 infra 层。
 */
public interface ToolOverrideProvider {

    /**
     * 获取指定工具的覆盖配置。
     *
     * @param toolName 工具名称
     * @return 覆盖配置，如果无覆盖则返回 empty
     */
    Optional<ToolOverride> getOverride(String toolName);

    /**
     * 工具覆盖配置记录。
     *
     * @param description 覆盖后的描述
     * @param inputSchema 覆盖后的输入参数 Schema（JSON）
     * @param dangerous   是否为危险操作
     */
    record ToolOverride(String description, String inputSchema, boolean dangerous) {
    }
}
