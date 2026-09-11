package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.GroupRatioConfig;
import com.mmcove.agent.infra.persistence.mapper.GroupRatioConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 分组倍率配置仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GroupRatioConfigRepository {

    private final GroupRatioConfigMapper groupRatioConfigMapper;

    /**
     * 按分组名查询倍率。
     */
    public Optional<GroupRatioConfig> findByGroupName(String groupName) {
        LambdaQueryWrapper<GroupRatioConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupRatioConfig::getGroupName, groupName);
        return Optional.ofNullable(groupRatioConfigMapper.selectOne(wrapper));
    }

    /**
     * 新增。
     */
    public GroupRatioConfig insert(GroupRatioConfig config) {
        groupRatioConfigMapper.insert(config);
        return config;
    }

    /**
     * 更新。
     */
    public boolean update(GroupRatioConfig config) {
        return groupRatioConfigMapper.updateById(config) > 0;
    }
}
