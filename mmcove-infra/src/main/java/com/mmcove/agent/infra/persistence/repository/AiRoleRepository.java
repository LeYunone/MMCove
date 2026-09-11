package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.AiRole;
import com.mmcove.agent.infra.persistence.mapper.AiRoleMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI角色仓库。
 * 启动时从 DB 加载全部 Role 到内存缓存，按 code 查询直接走内存。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AiRoleRepository {

    private final AiRoleMapper aiRoleMapper;

    /** 内存缓存：code → AiRole */
    private final ConcurrentHashMap<String, AiRole> roleCache = new ConcurrentHashMap<>();

    /**
     * 启动时加载全部 Role 到缓存。
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * 刷新缓存：从 DB 重新加载全部 Role。
     */
    public void refreshCache() {
        List<AiRole> roles = aiRoleMapper.selectList(null);
        ConcurrentHashMap<String, AiRole> newCache = new ConcurrentHashMap<>();
        for (AiRole role : roles) {
            newCache.put(role.getCode(), role);
        }
        roleCache.clear();
        roleCache.putAll(newCache);
        log.info("[AiRole缓存] 加载完成，共 {} 条角色记录", newCache.size());
    }

    /**
     * 按 code 查询角色（走内存缓存）。
     */
    public Optional<AiRole> findByCode(String code) {
        return Optional.ofNullable(roleCache.get(code));
    }

    /**
     * 查询全部角色。
     */
    public List<AiRole> findAll() {
        return aiRoleMapper.selectList(null);
    }

    /**
     * 按 ID 查询角色。
     */
    public Optional<AiRole> findById(Long id) {
        return Optional.ofNullable(aiRoleMapper.selectById(id));
    }

    /**
     * 搜索角色（按名称或编码模糊匹配）。
     */
    public List<AiRole> search(String keyword) {
        LambdaQueryWrapper<AiRole> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(AiRole::getName, keyword)
                    .or()
                    .like(AiRole::getCode, keyword));
        }
        wrapper.orderByDesc(AiRole::getId);
        return aiRoleMapper.selectList(wrapper);
    }

    /**
     * 新增角色。
     */
    public AiRole insert(AiRole role) {
        aiRoleMapper.insert(role);
        roleCache.put(role.getCode(), role);
        return role;
    }

    /**
     * 更新角色。
     */
    public boolean update(AiRole role) {
        boolean result = aiRoleMapper.updateById(role) > 0;
        if (result) {
            roleCache.put(role.getCode(), role);
        }
        return result;
    }

    /**
     * 删除角色。
     */
    public boolean delete(Long id) {
        AiRole role = aiRoleMapper.selectById(id);
        boolean result = aiRoleMapper.deleteById(id) > 0;
        if (result && role != null) {
            roleCache.remove(role.getCode());
        }
        return result;
    }
}
