package com.mmcove.agent.core.tool;

import com.mmcove.agent.common.model.entity.ToolDefinition;
import com.mmcove.agent.infra.persistence.repository.ToolDefinitionRepository;
import com.mmcove.agent.tools.registry.ToolOverrideProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具覆盖配置注册中心，启动时从数据库加载并缓存。
 * <p>
 * 管理后台修改工具描述后，调用 refreshCache() 刷新缓存，
 * 下次 LLM 调用时即可使用更新后的描述。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolOverrideRegistry implements ToolOverrideProvider {

    private final ToolDefinitionRepository toolDefinitionRepository;

    /** 按 toolName 缓存的覆盖配置 */
    private final ConcurrentHashMap<String, ToolOverride> overrideCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /**
     * 从数据库刷新缓存。
     */
    public void refreshCache() {
        List<ToolDefinition> definitions = toolDefinitionRepository.findAllActive();
        overrideCache.clear();
        for (ToolDefinition def : definitions) {
            overrideCache.put(def.getToolName(), new ToolOverride(
                    def.getDescription(),
                    def.getInputSchema(),
                    Boolean.TRUE.equals(def.getDangerous())
            ));
        }
        log.info("已加载 {} 个工具覆盖配置", overrideCache.size());
    }

    @Override
    public Optional<ToolOverride> getOverride(String toolName) {
        return Optional.ofNullable(overrideCache.get(toolName));
    }
}
