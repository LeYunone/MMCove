package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.SseEventRecord;
import com.mmcove.agent.infra.persistence.mapper.SseEventRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SSE 事件记录仓库。
 */
@Repository
@RequiredArgsConstructor
public class SseEventRepository {

    private final SseEventRecordMapper sseEventRecordMapper;

    public SseEventRecord save(SseEventRecord record) {
        sseEventRecordMapper.insert(record);
        return record;
    }

    /**
     * 按会话查询所有事件，按创建时间排序。
     */
    public List<SseEventRecord> findBySessionId(String sessionId) {
        LambdaQueryWrapper<SseEventRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SseEventRecord::getSessionId, sessionId)
                .orderByAsc(SseEventRecord::getCreatedAt);
        return sseEventRecordMapper.selectList(wrapper);
    }

    /**
     * 查询指定事件 ID 之后的事件（用于重放）。
     */
    public List<SseEventRecord> findAfterEventId(String sessionId, String lastEventId) {
        // 先查找 lastEventId 对应的记录
        LambdaQueryWrapper<SseEventRecord> idWrapper = new LambdaQueryWrapper<>();
        idWrapper.eq(SseEventRecord::getSessionId, sessionId)
                .eq(SseEventRecord::getEventId, lastEventId);
        SseEventRecord lastRecord = sseEventRecordMapper.selectOne(idWrapper);

        LambdaQueryWrapper<SseEventRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SseEventRecord::getSessionId, sessionId);
        if (lastRecord != null) {
            wrapper.gt(SseEventRecord::getId, lastRecord.getId());
        }
        wrapper.orderByAsc(SseEventRecord::getCreatedAt);
        return sseEventRecordMapper.selectList(wrapper);
    }
}
