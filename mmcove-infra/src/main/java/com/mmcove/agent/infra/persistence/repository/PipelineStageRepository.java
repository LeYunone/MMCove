package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.PipelineStage;
import com.mmcove.agent.infra.persistence.mapper.PipelineStageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 流水线阶段库仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PipelineStageRepository {

    private final PipelineStageMapper pipelineStageMapper;

    public Optional<PipelineStage> findByCode(String stageCode) {
        LambdaQueryWrapper<PipelineStage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStage::getStageCode, stageCode);
        return Optional.ofNullable(pipelineStageMapper.selectOne(wrapper));
    }

    public Optional<PipelineStage> findById(Long id) {
        return Optional.ofNullable(pipelineStageMapper.selectById(id));
    }

    /** 启用的全部阶段(Registry 缓存与管理端目录用) */
    public List<PipelineStage> findAllActive() {
        LambdaQueryWrapper<PipelineStage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStage::getStatus, 1)
                .orderByAsc(PipelineStage::getId);
        return pipelineStageMapper.selectList(wrapper);
    }

    public List<PipelineStage> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return pipelineStageMapper.selectBatchIds(ids);
    }

    public void insert(PipelineStage stage) {
        pipelineStageMapper.insert(stage);
    }

    public void update(PipelineStage stage) {
        pipelineStageMapper.updateById(stage);
    }

    public boolean deleteById(Long id) {
        return pipelineStageMapper.deleteById(id) > 0;
    }
}
