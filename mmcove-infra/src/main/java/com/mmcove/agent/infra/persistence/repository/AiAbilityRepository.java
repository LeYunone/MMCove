package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.AiAbility;
import com.mmcove.agent.infra.persistence.mapper.AiAbilityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模型能力映射仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AiAbilityRepository {

    private final AiAbilityMapper aiAbilityMapper;

    /**
     * 按 group + model 查询所有启用的能力（按优先级降序）。
     */
    public List<AiAbility> findEnabledByGroupAndModel(String groupName, String model) {
        LambdaQueryWrapper<AiAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAbility::getGroupName, groupName)
                .eq(AiAbility::getModel, model)
                .eq(AiAbility::getEnabled, 1)
                .orderByDesc(AiAbility::getPriority);
        return aiAbilityMapper.selectList(wrapper);
    }

    /**
     * 按 group 查询所有能力。
     */
    public List<AiAbility> findByGroup(String groupName) {
        LambdaQueryWrapper<AiAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAbility::getGroupName, groupName)
                .orderByDesc(AiAbility::getPriority);
        return aiAbilityMapper.selectList(wrapper);
    }

    /**
     * 按渠道ID查询所有能力。
     */
    public List<AiAbility> findByChannelId(Long channelId) {
        LambdaQueryWrapper<AiAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAbility::getChannelId, channelId);
        return aiAbilityMapper.selectList(wrapper);
    }

    /**
     * 查询所有能力。
     */
    public List<AiAbility> findAll() {
        return aiAbilityMapper.selectList(null);
    }

    /**
     * 查询所有可用的模型名称（去重）。
     */
    public List<String> findAllModelNames() {
        LambdaQueryWrapper<AiAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AiAbility::getModel)
                .eq(AiAbility::getEnabled, 1)
                .groupBy(AiAbility::getModel);
        return aiAbilityMapper.selectList(wrapper).stream()
                .map(AiAbility::getModel)
                .distinct()
                .toList();
    }

    /**
     * 新增能力。
     */
    public AiAbility insert(AiAbility ability) {
        aiAbilityMapper.insert(ability);
        return ability;
    }

    /**
     * 批量新增能力。
     */
    public void batchInsert(List<AiAbility> abilities) {
        for (AiAbility ability : abilities) {
            aiAbilityMapper.insert(ability);
        }
    }

    /**
     * 更新能力。
     */
    public boolean update(AiAbility ability) {
        return aiAbilityMapper.updateById(ability) > 0;
    }

    /**
     * 删除能力。
     */
    public boolean delete(Long id) {
        return aiAbilityMapper.deleteById(id) > 0;
    }

    /**
     * 按渠道ID删除所有能力。
     */
    public int deleteByChannelId(Long channelId) {
        LambdaQueryWrapper<AiAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAbility::getChannelId, channelId);
        return aiAbilityMapper.delete(wrapper);
    }

    /**
     * 按渠道ID更新启用状态。
     */
    public int updateEnabledByChannelId(Long channelId, int enabled) {
        AiAbility update = new AiAbility();
        update.setEnabled(enabled);
        LambdaQueryWrapper<AiAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAbility::getChannelId, channelId);
        return aiAbilityMapper.update(update, wrapper);
    }

    /**
     * 按分组和模型删除所有能力。
     */
    public int deleteByGroupAndModel(String groupName, String model) {
        LambdaQueryWrapper<AiAbility> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAbility::getGroupName, groupName)
                .eq(AiAbility::getModel, model);
        return aiAbilityMapper.delete(wrapper);
    }
}
