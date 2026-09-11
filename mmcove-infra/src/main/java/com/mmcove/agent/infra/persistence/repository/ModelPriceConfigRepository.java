package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.ModelPriceConfig;
import com.mmcove.agent.infra.persistence.mapper.ModelPriceConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 模型计费配置仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ModelPriceConfigRepository {

    private final ModelPriceConfigMapper modelPriceConfigMapper;

    /**
     * 按模型名查询价格配置。
     */
    public Optional<ModelPriceConfig> findByModelName(String modelName) {
        LambdaQueryWrapper<ModelPriceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelPriceConfig::getModelName, modelName)
                .eq(ModelPriceConfig::getEnabled, 1);
        return Optional.ofNullable(modelPriceConfigMapper.selectOne(wrapper));
    }

    /**
     * 查询所有启用的价格配置。
     */
    public List<ModelPriceConfig> findAllEnabled() {
        LambdaQueryWrapper<ModelPriceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelPriceConfig::getEnabled, 1);
        return modelPriceConfigMapper.selectList(wrapper);
    }

    /**
     * 查询全部。
     */
    public List<ModelPriceConfig> findAll() {
        return modelPriceConfigMapper.selectList(null);
    }

    /**
     * 新增。
     */
    public ModelPriceConfig insert(ModelPriceConfig config) {
        modelPriceConfigMapper.insert(config);
        return config;
    }

    /**
     * 更新。
     */
    public boolean update(ModelPriceConfig config) {
        return modelPriceConfigMapper.updateById(config) > 0;
    }
}
