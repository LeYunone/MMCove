package com.mmcove.agent.service;

import com.mmcove.agent.common.model.entity.AiAbility;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.infra.persistence.repository.AiAbilityRepository;
import com.mmcove.agent.infra.persistence.repository.AiChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 渠道负载均衡。
 * 基于 new-api 的 CacheGetRandomSatisfiedChannel 算法实现：
 * 按优先级分层，同优先级按权重随机选择。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelLoadBalancer {

    private final AiAbilityRepository aiAbilityRepository;
    private final AiChannelRepository aiChannelRepository;
    private final DynamicChatModelFactory dynamicChatModelFactory;

    /** 平滑因子，避免权重为0时无法选中 */
    private static final int SMOOTH_FACTOR = 10;

    /**
     * 选择渠道（无排除）。
     */
    public SelectedChannel selectChannel(String group, String model, int retry) {
        return selectChannel(group, model, retry, Collections.emptySet());
    }

    /**
     * 选择渠道（支持排除已试渠道）。
     *
     * @param group             分组名称
     * @param model             模型名称
     * @param retry             当前重试次数
     * @param excludeChannelIds 已失败、需排除的渠道ID集合
     * @return 选中的渠道及对应模型信息，null 表示无可用渠道
     */
    public SelectedChannel selectChannel(String group, String model, int retry, Set<Long> excludeChannelIds) {
        if (group == null || group.isEmpty()) {
            group = "default";
        }

        // 1. 查询 group+model 的所有启用能力，排除已试渠道
        List<AiAbility> abilities = aiAbilityRepository.findEnabledByGroupAndModel(group, model);
        if (excludeChannelIds != null && !excludeChannelIds.isEmpty()) {
            abilities = abilities.stream()
                    .filter(a -> !excludeChannelIds.contains(a.getChannelId()))
                    .collect(Collectors.toList());
        }
        if (abilities.isEmpty()) {
            log.warn("[LoadBalancer] 无可用能力: group={}, model={}, excluded={}", group, model, excludeChannelIds);
            return fallbackToSystem(group, model);
        }

        // 2. 收集所有唯一 priority，降序排列
        List<Long> priorities = abilities.stream()
                .map(AiAbility::getPriority)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        // 3. 根据 retry 选择对应优先级层级（同优先级渠道耗尽后才降级）
        int level = Math.min(retry, priorities.size() - 1);
        Long targetPriority = priorities.get(level);

        // 4. 同优先级渠道中，按权重随机选择
        List<AiAbility> samePriorityAbilities = abilities.stream()
                .filter(a -> a.getPriority().equals(targetPriority))
                .toList();

        AiAbility selected = weightedRandomSelect(samePriorityAbilities);
        if (selected == null) {
            log.warn("[LoadBalancer] 权重选择失败: group={}, model={}", group, model);
            return fallbackToSystem(group, model);
        }

        // 5. 查询渠道详情
        AiChannel channel = aiChannelRepository.findById(selected.getChannelId()).orElse(null);
        if (channel == null || !channel.isEnabled()) {
            log.warn("[LoadBalancer] 渠道不可用: channelId={}", selected.getChannelId());
            if (retry < priorities.size() - 1) {
                return selectChannel(group, model, retry + 1, excludeChannelIds);
            }
            return fallbackToSystem(group, model);
        }

        // 应用渠道的模型映射
        String mappedModel = applyModelMapping(channel, model);

        log.info("[LoadBalancer] 选中渠道: channelId={}, name={}, model={}->{}, priority={}, retry={}",
                channel.getId(), channel.getName(), model, mappedModel, targetPriority, retry);

        return new SelectedChannel(channel, mappedModel);
    }

    /**
     * 未指定模型时,按分组选取优先级最高的能力作为默认渠道。
     * <p>语义:该分组下优先级最高的那条 ability 对应的渠道+模型即为默认。
     * 同优先级多条时按权重随机,与 {@link #selectChannel} 一致。
     *
     * @param group 分组名称
     * @return 选中的渠道及对应模型,null 表示无可用渠道
     */
    public SelectedChannel selectDefaultByGroup(String group) {
        if (group == null || group.isEmpty()) {
            group = "default";
        }

        List<AiAbility> abilities = aiAbilityRepository.findByGroup(group).stream()
                .filter(a -> a.getEnabled() != null && a.getEnabled() == 1)
                .toList();
        if (abilities.isEmpty()) {
            log.warn("[LoadBalancer] 分组下无可用能力,无法选定默认: group={}", group);
            return fallbackToSystem(group, null);
        }

        // findByGroup 已按 priority 降序,取最高优先级那一层
        Long topPriority = abilities.get(0).getPriority();
        List<AiAbility> topAbilities = abilities.stream()
                .filter(a -> Objects.equals(a.getPriority(), topPriority))
                .toList();
        AiAbility selected = weightedRandomSelect(topAbilities);
        if (selected == null) {
            return fallbackToSystem(group, null);
        }

        AiChannel channel = aiChannelRepository.findById(selected.getChannelId()).orElse(null);
        if (channel == null || !channel.isEnabled()) {
            return fallbackToSystem(group, selected.getModel());
        }

        log.info("[LoadBalancer] 分组默认选中: channelId={}, name={}, model={}, group={}",
                channel.getId(), channel.getName(), selected.getModel(), group);
        return new SelectedChannel(channel, selected.getModel());
    }

    /**
     * 回退到系统默认渠道。
     */
    private SelectedChannel fallbackToSystem(String group, String model) {
        List<AiChannel> systemChannels = aiChannelRepository.findSystemChannels();
        if (systemChannels.isEmpty()) {
            log.error("[LoadBalancer] 无系统默认渠道");
            return null;
        }

        // 选取第一个启用的系统渠道
        AiChannel sysChannel = systemChannels.stream()
                .filter(AiChannel::isEnabled)
                .findFirst()
                .orElse(null);

        if (sysChannel == null) {
            log.error("[LoadBalancer] 系统渠道均不可用");
            return null;
        }

        String mappedModel = applyModelMapping(sysChannel, model);
        log.info("[LoadBalancer] 回退到系统渠道: channelId={}, name={}, model={}->{}", sysChannel.getId(), sysChannel.getName(), model, mappedModel);
        return new SelectedChannel(sysChannel, mappedModel);
    }

    /**
     * 加权随机选择。
     */
    private AiAbility weightedRandomSelect(List<AiAbility> abilities) {
        if (abilities.isEmpty()) {
            return null;
        }
        if (abilities.size() == 1) {
            return abilities.get(0);
        }

        // 计算总权重
        int totalWeight = 0;
        for (AiAbility ability : abilities) {
            totalWeight += getWeight(ability) + SMOOTH_FACTOR;
        }

        // 随机选择
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;
        for (AiAbility ability : abilities) {
            currentWeight += getWeight(ability) + SMOOTH_FACTOR;
            if (random < currentWeight) {
                return ability;
            }
        }

        return abilities.get(abilities.size() - 1);
    }

    private int getWeight(AiAbility ability) {
        return ability.getWeight() != null ? ability.getWeight() : 10;
    }

    /**
     * 选中的渠道信息。
     */
    public record SelectedChannel(AiChannel channel, String model) {
    }

    /**
     * 应用渠道的模型名映射。
     */
    private String applyModelMapping(AiChannel channel, String modelName) {
        String mapping = channel.getModelMapping();
        if (mapping == null || mapping.isEmpty()) {
            return modelName;
        }
        try {
            Map<String, String> mappingMap = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(mapping, new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            return mappingMap.getOrDefault(modelName, modelName);
        } catch (Exception e) {
            log.warn("[LoadBalancer] 解析模型映射失败: {}", e.getMessage());
            return modelName;
        }
    }
}
