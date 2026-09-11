package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.ProductLinePipeline;
import com.mmcove.agent.infra.persistence.mapper.ProductLinePipelineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 产品线↔流水线 绑定仓库。
 *
 * <p>全量替换(replaceAll) 仿 {@link AgentKnowledgeBaseRepository#replaceAll},
 * 供管理端"流水线配套"多选保存。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductLinePipelineRepository {

    private final ProductLinePipelineMapper productLinePipelineMapper;

    public Optional<ProductLinePipeline> find(Long productLineId, Long pipelineId) {
        LambdaQueryWrapper<ProductLinePipeline> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductLinePipeline::getProductLineId, productLineId)
                .eq(ProductLinePipeline::getPipelineId, pipelineId);
        return Optional.ofNullable(productLinePipelineMapper.selectOne(wrapper));
    }

    /** 全部绑定(Registry 缓存装载用) */
    public List<ProductLinePipeline> findAll() {
        LambdaQueryWrapper<ProductLinePipeline> wrapper = new LambdaQueryWrapper<>();
        return productLinePipelineMapper.selectList(wrapper);
    }

    public List<ProductLinePipeline> findByProductLineId(Long productLineId) {
        LambdaQueryWrapper<ProductLinePipeline> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductLinePipeline::getProductLineId, productLineId);
        return productLinePipelineMapper.selectList(wrapper);
    }

    /** 删除守卫:某流水线被多少产品线绑定(>0 拒删流水线) */
    public List<ProductLinePipeline> findByPipelineId(Long pipelineId) {
        LambdaQueryWrapper<ProductLinePipeline> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductLinePipeline::getPipelineId, pipelineId);
        return productLinePipelineMapper.selectList(wrapper);
    }

    /** 全量替换某产品线的流水线绑定(先删后插,管理端配套保存) */
    @Transactional
    public void replaceAll(Long productLineId, List<ProductLinePipeline> bindings) {
        LambdaQueryWrapper<ProductLinePipeline> del = new LambdaQueryWrapper<>();
        del.eq(ProductLinePipeline::getProductLineId, productLineId);
        productLinePipelineMapper.delete(del);

        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        for (ProductLinePipeline binding : bindings) {
            binding.setId(null);
            binding.setProductLineId(productLineId);
            productLinePipelineMapper.insert(binding);
        }
    }
}
