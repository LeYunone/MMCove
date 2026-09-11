package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.ThinkingTurn;
import com.mmcove.agent.infra.persistence.mapper.ThinkingTurnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 思考轮次仓库。
 */
@Repository
@RequiredArgsConstructor
public class ThinkingTurnRepository {

    private final ThinkingTurnMapper thinkingTurnMapper;

    public ThinkingTurn save(ThinkingTurn turn) {
        thinkingTurnMapper.insert(turn);
        return turn;
    }

    public void update(ThinkingTurn turn) {
        thinkingTurnMapper.updateById(turn);
    }

    /**
     * 按会话和子任务查询思考轮次。
     */
    public List<ThinkingTurn> findBySessionId(String sessionId, String subTaskId) {
        LambdaQueryWrapper<ThinkingTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThinkingTurn::getSessionId, sessionId);
        if (subTaskId != null) {
            wrapper.eq(ThinkingTurn::getSubTaskId, subTaskId);
        }
        wrapper.orderByAsc(ThinkingTurn::getTurnIndex);
        return thinkingTurnMapper.selectList(wrapper);
    }
}
