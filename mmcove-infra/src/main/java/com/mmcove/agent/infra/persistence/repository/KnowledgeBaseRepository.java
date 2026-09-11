package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.infra.persistence.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class KnowledgeBaseRepository {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /** 列表:所有启用的知识库(按优先级降序;项目多租户未启用,不按 tenantId 过滤) */
    public List<KnowledgeBase> findAllEnabled() {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getStatus, 1)
                .orderByDesc(KnowledgeBase::getPriority);
        return knowledgeBaseMapper.selectList(wrapper);
    }

    public Optional<KnowledgeBase> findById(Long id) {
        return Optional.ofNullable(knowledgeBaseMapper.selectById(id));
    }

    public Optional<KnowledgeBase> findByName(String name) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getName, name);
        return Optional.ofNullable(knowledgeBaseMapper.selectOne(wrapper));
    }

    /** 选库用:按 id 集合批量取启用的知识库(带 description/keywords 供路由判断) */
    public List<KnowledgeBase> findEnabledByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KnowledgeBase::getId, ids).eq(KnowledgeBase::getStatus, 1);
        return knowledgeBaseMapper.selectList(wrapper);
    }

    public void insert(KnowledgeBase kb) {
        knowledgeBaseMapper.insert(kb);
    }

    public void update(KnowledgeBase kb) {
        knowledgeBaseMapper.updateById(kb);
    }

    public boolean deleteById(Long id) {
        return knowledgeBaseMapper.deleteById(id) > 0;
    }
    /** 产品线隔离查询:指定线集合的私有库 + 全部共享库(启用) */
    public java.util.List<KnowledgeBase> findEnabledByProductLinesIncludeShared(
            java.util.List<Long> productLineIds) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getStatus, 1)
                .and(w -> w.in(productLineIds != null && !productLineIds.isEmpty(),
                                KnowledgeBase::getProductLineId, productLineIds)
                        .or().eq(KnowledgeBase::getIsShared, true));
        return knowledgeBaseMapper.selectList(wrapper);
    }

}
