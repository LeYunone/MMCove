package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.AiRoleChannelGroup;
import com.mmcove.agent.infra.persistence.mapper.AiRoleChannelGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色-渠道-分组关联仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AiRoleChannelGroupRepository {

    private final AiRoleChannelGroupMapper aiRoleChannelGroupMapper;

    /**
     * 按 group + roleCode 查询关联列表。
     */
    public List<AiRoleChannelGroup> findByGroupAndRoleCode(String groupName, String roleCode) {
        LambdaQueryWrapper<AiRoleChannelGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiRoleChannelGroup::getGroupName, groupName)
                .eq(AiRoleChannelGroup::getRoleCode, roleCode)
                .orderByDesc(AiRoleChannelGroup::getPriority);
        return aiRoleChannelGroupMapper.selectList(wrapper);
    }

    /**
     * 查询全部关联。
     */
    public List<AiRoleChannelGroup> findAll() {
        return aiRoleChannelGroupMapper.selectList(null);
    }

    /**
     * 按 ID 查询。
     */
    public Optional<AiRoleChannelGroup> findById(Long id) {
        return Optional.ofNullable(aiRoleChannelGroupMapper.selectById(id));
    }

    /**
     * 新增关联。
     */
    public AiRoleChannelGroup insert(AiRoleChannelGroup entity) {
        aiRoleChannelGroupMapper.insert(entity);
        return entity;
    }

    /**
     * 更新关联。
     */
    public boolean update(AiRoleChannelGroup entity) {
        return aiRoleChannelGroupMapper.updateById(entity) > 0;
    }

    /**
     * 删除关联。
     */
    public boolean delete(Long id) {
        return aiRoleChannelGroupMapper.deleteById(id) > 0;
    }
}
