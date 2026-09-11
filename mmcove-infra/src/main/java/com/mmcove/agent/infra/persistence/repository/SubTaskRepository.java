package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mmcove.agent.common.model.entity.SubTask;
import com.mmcove.agent.infra.persistence.mapper.SubTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 子任务仓库。
 */
@Repository
@RequiredArgsConstructor
public class SubTaskRepository {

    private final SubTaskMapper subTaskMapper;

    public SubTask save(SubTask subTask) {
        subTaskMapper.insert(subTask);
        return subTask;
    }

    public void update(SubTask subTask) {
        subTaskMapper.updateById(subTask);
    }

    /**
     * 按 sessionId + taskIndex 精确更新状态（避免全表扫描）。
     */
    public void updateStatusBySessionAndIndex(String sessionId, int taskIndex, String status) {
        LambdaUpdateWrapper<SubTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SubTask::getSessionId, sessionId)
                .eq(SubTask::getTaskIndex, taskIndex)
                .set(SubTask::getStatus, status);
        subTaskMapper.update(null, wrapper);
    }

    /**
     * 按 sessionId + taskIndex 精确更新状态和结果。
     */
    public void updateResultBySessionAndIndex(String sessionId, int taskIndex,
                                               String status, String result) {
        LambdaUpdateWrapper<SubTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SubTask::getSessionId, sessionId)
                .eq(SubTask::getTaskIndex, taskIndex)
                .set(SubTask::getStatus, status)
                .set(result != null, SubTask::getResult, result);
        subTaskMapper.update(null, wrapper);
    }

    /**
     * 按会话查询所有子任务，按 taskIndex 排序。
     */
    public List<SubTask> findBySessionId(String sessionId) {
        LambdaQueryWrapper<SubTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubTask::getSessionId, sessionId)
                .orderByAsc(SubTask::getTaskIndex);
        return subTaskMapper.selectList(wrapper);
    }
}
