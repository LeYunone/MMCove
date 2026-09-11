package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.PipelineStageStep;
import com.mmcove.agent.infra.persistence.mapper.PipelineStageStepMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 流水线阶段序列仓库。
 *
 * <p>全量替换(replaceAll) 仿 {@link AgentKnowledgeBaseRepository#replaceAll},
 * 供管理端/YAML 导入整存整取步骤序列。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PipelineStageStepRepository {

    private final PipelineStageStepMapper pipelineStageStepMapper;

    /** 某流水线的步骤序列(按 seq 升序) */
    public List<PipelineStageStep> findByPipelineIdOrderBySeq(Long pipelineId) {
        LambdaQueryWrapper<PipelineStageStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStageStep::getPipelineId, pipelineId)
                .orderByAsc(PipelineStageStep::getSeq);
        return pipelineStageStepMapper.selectList(wrapper);
    }

    /** 全量替换某流水线的步骤序列(先删后插,管理端/YAML 导入保存) */
    @Transactional
    public void replaceAll(Long pipelineId, List<PipelineStageStep> steps) {
        LambdaQueryWrapper<PipelineStageStep> del = new LambdaQueryWrapper<>();
        del.eq(PipelineStageStep::getPipelineId, pipelineId);
        pipelineStageStepMapper.delete(del);

        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (PipelineStageStep step : steps) {
            step.setId(null);
            step.setPipelineId(pipelineId);
            pipelineStageStepMapper.insert(step);
        }
    }

    public int deleteByPipelineId(Long pipelineId) {
        LambdaQueryWrapper<PipelineStageStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStageStep::getPipelineId, pipelineId);
        return pipelineStageStepMapper.delete(wrapper);
    }

    /** 删除守卫:某阶段被多少流水线引用(>0 拒删,防改一处坏全线) */
    public long countByStageId(Long stageId) {
        LambdaQueryWrapper<PipelineStageStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStageStep::getStageId, stageId);
        return pipelineStageStepMapper.selectCount(wrapper);
    }

    /** 批量查询多流水线的步骤(导出/缓存装载用) */
    public List<PipelineStageStep> findByPipelineIds(Collection<Long> pipelineIds) {
        if (pipelineIds == null || pipelineIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<PipelineStageStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PipelineStageStep::getPipelineId, pipelineIds)
                .orderByAsc(PipelineStageStep::getSeq);
        return pipelineStageStepMapper.selectList(wrapper);
    }
}
