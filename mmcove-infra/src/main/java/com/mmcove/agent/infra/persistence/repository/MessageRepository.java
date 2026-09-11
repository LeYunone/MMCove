package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.entity.ConversationMessage;
import com.mmcove.agent.infra.persistence.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息增删改查及分页查询仓库。
 */
@Repository
@RequiredArgsConstructor
public class MessageRepository {

    private final ConversationMessageMapper messageMapper;

    public ConversationMessage save(ConversationMessage message) {
        messageMapper.insert(message);
        return message;
    }

    public List<ConversationMessage> findBySessionId(String sessionId) {
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getSessionId, sessionId)
                .orderByAsc(ConversationMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    public Page<ConversationMessage> findBySessionIdPaged(String sessionId, int pageNum, int pageSize) {
        Page<ConversationMessage> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getSessionId, sessionId)
                .orderByDesc(ConversationMessage::getCreatedAt);
        return messageMapper.selectPage(page, wrapper);
    }

    public void deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getSessionId, sessionId);
        messageMapper.delete(wrapper);
    }

    /**
     * 取会话最近 N 条消息(按时间正序返回,保证对话顺序)。
     * <p>「精准防飘移」上下文隔离用:替代全量 findBySessionId,只带最近 N 条喂给 LLM,
     * 避免会话越长历史越多导致 AI 被旧话题带偏。
     * <p>实现:orderByDesc + LIMIT N 取最近 N 条(倒序),再 reverse 回正序。
     */
    public List<ConversationMessage> findRecentBySessionId(String sessionId, int lastN) {
        if (lastN <= 0) {
            return List.of();
        }
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getSessionId, sessionId)
                .orderByDesc(ConversationMessage::getCreatedAt)
                .last("LIMIT " + lastN);
        List<ConversationMessage> recent = messageMapper.selectList(wrapper);
        java.util.Collections.reverse(recent);
        return recent;
    }

    /**
     * 取会话最近一条 assistant 消息(供重试提示判断用,避免全量拉取)。
     */
    public ConversationMessage findLastAssistantBySessionId(String sessionId) {
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getSessionId, sessionId)
                .eq(ConversationMessage::getRole, "assistant")
                .orderByDesc(ConversationMessage::getCreatedAt)
                .last("LIMIT 1");
        List<ConversationMessage> list = messageMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }
}
