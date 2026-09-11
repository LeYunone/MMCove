package com.mmcove.agent.core.template;

import com.mmcove.agent.common.model.entity.ResponseTemplate;
import com.mmcove.agent.infra.persistence.repository.ResponseTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 响应格式模板注册中心，启动时从数据库加载并缓存。
 * 支持按 scope 和 agentId 过滤模板。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseTemplateRegistry {

    private final ResponseTemplateRepository responseTemplateRepository;

    /** 按 scope 分组的缓存：scope -> 模板列表 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ResponseTemplate>> scopeCache = new ConcurrentHashMap<>();

    /** 全量缓存（活跃模板） */
    private volatile List<ResponseTemplate> allTemplates = new ArrayList<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /**
     * 从数据库刷新缓存。
     */
    public void refreshCache() {
        List<ResponseTemplate> templates = responseTemplateRepository.findAllActive();
        allTemplates = new ArrayList<>(templates);

        scopeCache.clear();
        for (ResponseTemplate t : templates) {
            scopeCache.computeIfAbsent(t.getScope(), k -> new CopyOnWriteArrayList<>()).add(t);
        }

        log.info("已加载 {} 个响应模板（SCENE: {}, TOOL: {}）",
                templates.size(),
                scopeCache.getOrDefault("SCENE", new CopyOnWriteArrayList<>()).size(),
                scopeCache.getOrDefault("TOOL", new CopyOnWriteArrayList<>()).size());
    }

    /**
     * 获取指定 Agent 适用的模板，按 scope 分组。
     * 优先返回绑定该 agentId 的模板，其次返回全局模板（agentId 为空）。
     *
     * @param scope   作用域：SCENE / TOOL
     * @param agentId Agent ID，可为 null
     * @return 匹配的模板列表
     */
    public List<ResponseTemplate> getTemplates(String scope, String agentId) {
        List<ResponseTemplate> scoped = scopeCache.getOrDefault(scope, new CopyOnWriteArrayList<>());
        return scoped.stream()
                .filter(t -> t.getAgentId() == null || t.getAgentId().isBlank()
                        || t.getAgentId().equals(agentId))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃模板。
     */
    public List<ResponseTemplate> getAllTemplates() {
        return List.copyOf(allTemplates);
    }
}
