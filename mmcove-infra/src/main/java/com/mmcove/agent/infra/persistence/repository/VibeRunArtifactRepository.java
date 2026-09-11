package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.VibeRunArtifact;
import com.mmcove.agent.infra.persistence.mapper.VibeRunArtifactMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Vibe 阶段产物仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VibeRunArtifactRepository {

    private final VibeRunArtifactMapper vibeRunArtifactMapper;

    /** 某执行实例的全部产物(按上报时间升序 = 阶段推进顺序) */
    public List<VibeRunArtifact> findByRunId(Long runId) {
        LambdaQueryWrapper<VibeRunArtifact> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VibeRunArtifact::getRunId, runId)
                .orderByAsc(VibeRunArtifact::getId);
        return vibeRunArtifactMapper.selectList(wrapper);
    }

    public void insert(VibeRunArtifact artifact) {
        vibeRunArtifactMapper.insert(artifact);
    }
}
