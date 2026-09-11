package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.GroupToolConfig;
import com.mmcove.agent.infra.persistence.mapper.GroupToolConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分组工具配置仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GroupToolConfigRepository {

    private final GroupToolConfigMapper groupToolConfigMapper;

    /**
     * 查询分组下启用的工具名列表。
     */
    public List<String> findEnabledToolNamesByGroup(String groupName) {
        LambdaQueryWrapper<GroupToolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupToolConfig::getGroupName, groupName)
                .eq(GroupToolConfig::getEnabled, 1)
                .select(GroupToolConfig::getToolName);
        return groupToolConfigMapper.selectList(wrapper)
                .stream()
                .map(GroupToolConfig::getToolName)
                .toList();
    }

    /**
     * 查询分组下所有配置。
     */
    public List<GroupToolConfig> findByGroup(String groupName) {
        LambdaQueryWrapper<GroupToolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupToolConfig::getGroupName, groupName);
        return groupToolConfigMapper.selectList(wrapper);
    }

    /**
     * 批量保存分组工具配置（全量替换）。
     */
    public void replaceAll(String groupName, List<String> toolNames) {
        // 先删除该分组所有配置
        LambdaQueryWrapper<GroupToolConfig> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(GroupToolConfig::getGroupName, groupName);
        groupToolConfigMapper.delete(deleteWrapper);

        // 批量插入
        for (String toolName : toolNames) {
            GroupToolConfig config = new GroupToolConfig();
            config.setGroupName(groupName);
            config.setToolName(toolName);
            config.setEnabled(1);
            groupToolConfigMapper.insert(config);
        }
    }

    /**
     * 删除分组下的指定工具。
     */
    public boolean remove(String groupName, String toolName) {
        LambdaQueryWrapper<GroupToolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupToolConfig::getGroupName, groupName)
                .eq(GroupToolConfig::getToolName, toolName);
        return groupToolConfigMapper.delete(wrapper) > 0;
    }

    /**
     * 查询所有分组名(distinct,供后台分组列表展示)。
     * <p>「工具分组可视化管理」:分组 = group_tool_config 里 distinct 的 group_name(不建实体表)。
     */
    public List<String> findAllGroupNames() {
        LambdaQueryWrapper<GroupToolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(GroupToolConfig::getGroupName);
        return groupToolConfigMapper.selectList(wrapper).stream()
                .map(GroupToolConfig::getGroupName)
                .filter(g -> g != null && !g.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 删除整组配置(删分组用)。
     */
    public int deleteByGroup(String groupName) {
        LambdaQueryWrapper<GroupToolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupToolConfig::getGroupName, groupName);
        return groupToolConfigMapper.delete(wrapper);
    }
}
