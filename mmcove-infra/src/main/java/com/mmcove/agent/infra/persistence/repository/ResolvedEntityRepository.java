package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.ResolvedEntity;
import com.mmcove.agent.infra.persistence.mapper.ResolvedEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 已解析实体记忆仓库。
 *
 * @since 2026-07-21
 */
@Repository
@RequiredArgsConstructor
public class ResolvedEntityRepository {

    private final ResolvedEntityMapper resolvedEntityMapper;

    /**
     * 查会话下未过期的实体(注入 LLM 上下文用,跨轮指代匹配)。
     * 未过期 = expires_at 为空 或 >= 当前时间。按创建时间倒序(最近优先)。
     */
    public List<ResolvedEntity> findActiveBySession(String sessionId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<ResolvedEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResolvedEntity::getSessionId, sessionId)
                .and(w -> w.isNull(ResolvedEntity::getExpiresAt)
                        .or().ge(ResolvedEntity::getExpiresAt, now))
                .orderByDesc(ResolvedEntity::getCreatedAt);
        return resolvedEntityMapper.selectList(wrapper);
    }

    /**
     * upsert 保存:同 session + displayName + type 先删后插(更新最新解析结果)。
     * 加事务保证 delete+insert 原子性,防并发重复/中断丢数据(配合唯一索引更稳)。
     */
    @Transactional
    public void save(ResolvedEntity entity) {
        LambdaQueryWrapper<ResolvedEntity> del = new LambdaQueryWrapper<>();
        del.eq(ResolvedEntity::getSessionId, entity.getSessionId())
                .eq(ResolvedEntity::getDisplayName, entity.getDisplayName())
                .eq(ResolvedEntity::getEntityType, entity.getEntityType());
        resolvedEntityMapper.delete(del);
        resolvedEntityMapper.insert(entity);
    }

    /**
     * 删除会话下所有实体(会话结束时清理)。
     */
    public void deleteBySession(String sessionId) {
        LambdaQueryWrapper<ResolvedEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResolvedEntity::getSessionId, sessionId);
        resolvedEntityMapper.delete(wrapper);
    }
}
