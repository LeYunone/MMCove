package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.Conversation;
import com.mmcove.agent.infra.persistence.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会话增删改查仓库。
 */
@Repository
@RequiredArgsConstructor
public class ConversationRepository {

    private final ConversationMapper conversationMapper;

    public Conversation save(Conversation conversation) {
        conversationMapper.insert(conversation);
        return conversation;
    }

    public Optional<Conversation> findBySessionId(String sessionId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getSessionId, sessionId);
        return Optional.ofNullable(conversationMapper.selectOne(wrapper));
    }

    public List<Conversation> findByUserId(String userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
                .eq(Conversation::getStatus, "ACTIVE")
                .orderByDesc(Conversation::getUpdatedAt);
        return conversationMapper.selectList(wrapper);
    }

    public void update(Conversation conversation) {
        conversationMapper.updateById(conversation);
    }

    public void deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getSessionId, sessionId);
        conversationMapper.delete(wrapper);
    }

    public List<Conversation> findAllActive() {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getStatus, "ACTIVE")
                .orderByDesc(Conversation::getUpdatedAt);
        return conversationMapper.selectList(wrapper);
    }

}
