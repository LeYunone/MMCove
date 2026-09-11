package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.infra.persistence.mapper.AiChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI渠道仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AiChannelRepository {

    private final AiChannelMapper aiChannelMapper;

    /**
     * 按 ID 查询渠道。
     */
    public Optional<AiChannel> findById(Long id) {
        return Optional.ofNullable(aiChannelMapper.selectById(id));
    }

    /**
     * 按分组 + 角色查询启用的渠道（role 字段逗号分隔，按优先级降序）。
     */
    public List<AiChannel> findByGroupNameAndRole(String groupName, String roleCode) {
        LambdaQueryWrapper<AiChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChannel::getGroupName, groupName)
                .eq(AiChannel::getStatus, 1)
                .like(AiChannel::getRole, roleCode)
                .orderByDesc(AiChannel::getPriority);
        return aiChannelMapper.selectList(wrapper);
    }

    /**
     * 查询全部渠道。
     */
    public List<AiChannel> findAll() {
        return aiChannelMapper.selectList(null);
    }

    /**
     * 查询启用的渠道。
     */
    public List<AiChannel> findAllEnabled() {
        LambdaQueryWrapper<AiChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChannel::getStatus, 1);
        return aiChannelMapper.selectList(wrapper);
    }

    /**
     * 查询系统默认渠道。
     */
    public List<AiChannel> findSystemChannels() {
        LambdaQueryWrapper<AiChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChannel::getIsSystem, 1);
        return aiChannelMapper.selectList(wrapper);
    }

    /**
     * 新增渠道。
     */
    public AiChannel insert(AiChannel channel) {
        aiChannelMapper.insert(channel);
        return channel;
    }

    /**
     * 更新渠道。
     */
    public boolean update(AiChannel channel) {
        return aiChannelMapper.updateById(channel) > 0;
    }

    /**
     * 删除渠道。
     */
    public boolean delete(Long id) {
        return aiChannelMapper.deleteById(id) > 0;
    }

    /**
     * 更新渠道已使用配额。
     */
    public boolean increaseUsedQuota(Long channelId, long quota) {
        LambdaUpdateWrapper<AiChannel> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiChannel::getId, channelId)
                .setSql("used_quota = COALESCE(used_quota, 0) + " + quota);
        return aiChannelMapper.update(null, wrapper) > 0;
    }

    /**
     * 更新渠道响应时间。
     */
    public boolean updateResponseTime(Long channelId, int responseTimeMs) {
        AiChannel update = new AiChannel();
        update.setId(channelId);
        update.setResponseTime(responseTimeMs);
        return aiChannelMapper.updateById(update) > 0;
    }
}
