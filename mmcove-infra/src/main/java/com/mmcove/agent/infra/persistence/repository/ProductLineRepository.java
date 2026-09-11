package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.KnowledgeBase;
import com.mmcove.agent.common.model.entity.ProductLine;
import com.mmcove.agent.infra.persistence.mapper.KnowledgeBaseMapper;
import com.mmcove.agent.infra.persistence.mapper.ProductLineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 产品线仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductLineRepository {

    private final ProductLineMapper productLineMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /** 全量列表(按 id 升序,管理端目录用) */
    public List<ProductLine> findAll() {
        LambdaQueryWrapper<ProductLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ProductLine::getId);
        return productLineMapper.selectList(wrapper);
    }

    public Optional<ProductLine> findById(Long id) {
        return Optional.ofNullable(productLineMapper.selectById(id));
    }

    public Optional<ProductLine> findByCode(String code) {
        LambdaQueryWrapper<ProductLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductLine::getCode, code);
        return Optional.ofNullable(productLineMapper.selectOne(wrapper));
    }

    public void insert(ProductLine line) {
        productLineMapper.insert(line);
    }

    public void update(ProductLine line) {
        productLineMapper.updateById(line);
    }

    public boolean deleteById(Long id) {
        return productLineMapper.deleteById(id) > 0;
    }

    /** 删除守卫:该产品线下挂载的知识库数量 */
    public long countKnowledgeBases(Long lineId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getProductLineId, lineId);
        return knowledgeBaseMapper.selectCount(wrapper);
    }
}
