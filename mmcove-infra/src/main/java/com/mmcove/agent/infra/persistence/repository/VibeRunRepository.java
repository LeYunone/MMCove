package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.entity.VibeRun;
import com.mmcove.agent.infra.persistence.mapper.VibeRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Vibe 任务执行实例仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VibeRunRepository {

    private final VibeRunMapper vibeRunMapper;

    public Optional<VibeRun> findByRunCode(String runCode) {
        LambdaQueryWrapper<VibeRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VibeRun::getRunCode, runCode);
        return Optional.ofNullable(vibeRunMapper.selectOne(wrapper));
    }

    public Optional<VibeRun> findById(Long id) {
        return Optional.ofNullable(vibeRunMapper.selectById(id));
    }

    /** getRunContext 兜底:某产品线最近活跃(RUNNING)的执行实例 */
    public Optional<VibeRun> findLatestRunningByLine(Long productLineId) {
        LambdaQueryWrapper<VibeRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VibeRun::getProductLineId, productLineId)
                .eq(VibeRun::getStatus, "RUNNING")
                .orderByDesc(VibeRun::getId)
                .last("LIMIT 1");
        return Optional.ofNullable(vibeRunMapper.selectOne(wrapper));
    }

    /**
     * getRunContext 兜底(带归属隔离):同产品线下,优先取当前调用方(token)自己的最近活跃实例,
     * 防止跨调用方劫持;无归属或无本方实例时回退全产品线查询。
     */
    public Optional<VibeRun> findLatestRunningForOwner(Long productLineId, Long ownerTokenId) {
        if (ownerTokenId != null) {
            LambdaQueryWrapper<VibeRun> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(VibeRun::getProductLineId, productLineId)
                    .eq(VibeRun::getOwnerTokenId, ownerTokenId)
                    .eq(VibeRun::getStatus, "RUNNING")
                    .orderByDesc(VibeRun::getId)
                    .last("LIMIT 1");
            VibeRun owned = vibeRunMapper.selectOne(wrapper);
            if (owned != null) {
                return Optional.of(owned);
            }
        }
        return findLatestRunningByLine(productLineId);
    }

    /** 僵尸收割:把闲置超阈值的 RUNNING 实例置 ABORTED(低频写时清扫,限流50条) */
    public int sweepIdleRunning(int idleHours) {
        java.time.LocalDateTime deadline = java.time.LocalDateTime.now().minusHours(idleHours);
        LambdaQueryWrapper<VibeRun> query = new LambdaQueryWrapper<>();
        query.eq(VibeRun::getStatus, "RUNNING")
                .lt(VibeRun::getUpdatedAt, deadline)
                .last("LIMIT 50");
        java.util.List<VibeRun> stale = vibeRunMapper.selectList(query);
        for (VibeRun run : stale) {
            run.setStatus("ABORTED");
            vibeRunMapper.updateById(run);
        }
        return stale.size();
    }

    /** 管理端执行轨迹:按产品线过滤的分页列表(倒序) */
    public Page<VibeRun> pageByLine(Long productLineId, int pageNumber, int pageSize) {
        LambdaQueryWrapper<VibeRun> wrapper = new LambdaQueryWrapper<>();
        if (productLineId != null) {
            wrapper.eq(VibeRun::getProductLineId, productLineId);
        }
        wrapper.orderByDesc(VibeRun::getId);
        return vibeRunMapper.selectPage(Page.of(pageNumber, pageSize), wrapper);
    }

    public void insert(VibeRun run) {
        vibeRunMapper.insert(run);
    }

    public void update(VibeRun run) {
        vibeRunMapper.updateById(run);
    }

    /** 管理端可观测:某产品线活跃实例数 */
    public long countRunningByLine(Long productLineId) {
        LambdaQueryWrapper<VibeRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VibeRun::getProductLineId, productLineId)
                .eq(VibeRun::getStatus, "RUNNING");
        return vibeRunMapper.selectCount(wrapper);
    }

    /** 某流水线的历史执行实例(删流水线前的守卫数据) */
    public List<VibeRun> findByPipelineId(Long pipelineId) {
        LambdaQueryWrapper<VibeRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VibeRun::getPipelineId, pipelineId);
        return vibeRunMapper.selectList(wrapper);
    }
}
