package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.PipelineTemplate;
import com.mmcove.agent.infra.persistence.mapper.PipelineTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 流水线模板仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PipelineTemplateRepository {

    private final PipelineTemplateMapper pipelineTemplateMapper;

    /** 按意图取启用的全局共享流水线(product_line_id IS NULL) */
    public Optional<PipelineTemplate> findSharedByIntentType(String intentType) {
        LambdaQueryWrapper<PipelineTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineTemplate::getIntentType, intentType)
                .isNull(PipelineTemplate::getProductLineId)
                .eq(PipelineTemplate::getStatus, 1);
        return Optional.ofNullable(pipelineTemplateMapper.selectOne(wrapper));
    }

    /** 按意图取某产品线的启用私有副本 */
    public Optional<PipelineTemplate> findPrivateByIntentAndLine(String intentType, Long productLineId) {
        if (productLineId == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PipelineTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineTemplate::getIntentType, intentType)
                .eq(PipelineTemplate::getProductLineId, productLineId)
                .eq(PipelineTemplate::getStatus, 1);
        return Optional.ofNullable(pipelineTemplateMapper.selectOne(wrapper));
    }

    public Optional<PipelineTemplate> findByCode(String pipelineCode) {
        LambdaQueryWrapper<PipelineTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineTemplate::getPipelineCode, pipelineCode);
        return Optional.ofNullable(pipelineTemplateMapper.selectOne(wrapper));
    }

    public List<PipelineTemplate> findAll() {
        LambdaQueryWrapper<PipelineTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(PipelineTemplate::getId);
        return pipelineTemplateMapper.selectList(wrapper);
    }

    public Optional<PipelineTemplate> findById(Long id) {
        return Optional.ofNullable(pipelineTemplateMapper.selectById(id));
    }

    public void insert(PipelineTemplate template) {
        pipelineTemplateMapper.insert(template);
    }

    public void update(PipelineTemplate template) {
        pipelineTemplateMapper.updateById(template);
    }

    public boolean deleteById(Long id) {
        return pipelineTemplateMapper.deleteById(id) > 0;
    }
}
