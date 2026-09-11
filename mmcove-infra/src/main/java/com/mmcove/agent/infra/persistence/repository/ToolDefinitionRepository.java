package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.entity.ToolDefinition;
import com.mmcove.agent.infra.persistence.mapper.ToolDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 工具定义增删改查仓库。
 */
@Repository
@RequiredArgsConstructor
public class ToolDefinitionRepository {

    private final ToolDefinitionMapper toolDefinitionMapper;

    /**
     * 查询所有活跃的工具定义。
     */
    public List<ToolDefinition> findAllActive() {
        LambdaQueryWrapper<ToolDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolDefinition::getStatus, "ACTIVE")
                .orderByAsc(ToolDefinition::getId);
        return toolDefinitionMapper.selectList(wrapper);
    }

    /**
     * 分页查询工具定义。
     */
    public Page<ToolDefinition> findPage(int pageNum, int pageSize, String sourceClass, String keyword) {
        LambdaQueryWrapper<ToolDefinition> wrapper = new LambdaQueryWrapper<>();
        if (sourceClass != null && !sourceClass.isEmpty()) {
            wrapper.eq(ToolDefinition::getSourceClass, sourceClass);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(ToolDefinition::getToolName, keyword)
                    .or().like(ToolDefinition::getDescription, keyword));
        }
        wrapper.orderByAsc(ToolDefinition::getId);
        return toolDefinitionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 根据 ID 查询。
     */
    public Optional<ToolDefinition> findById(Long id) {
        return Optional.ofNullable(toolDefinitionMapper.selectById(id));
    }

    /**
     * 根据工具名查询。
     */
    public Optional<ToolDefinition> findByToolName(String toolName) {
        LambdaQueryWrapper<ToolDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolDefinition::getToolName, toolName);
        return Optional.ofNullable(toolDefinitionMapper.selectOne(wrapper));
    }

    /**
     * 查询所有来源类（去重）。
     */
    public List<String> findDistinctSourceClasses() {
        LambdaQueryWrapper<ToolDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ToolDefinition::getSourceClass)
                .isNotNull(ToolDefinition::getSourceClass)
                .groupBy(ToolDefinition::getSourceClass);
        return toolDefinitionMapper.selectList(wrapper).stream()
                .map(ToolDefinition::getSourceClass)
                .filter(sc -> sc != null && !sc.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * 新增工具定义。
     */
    public ToolDefinition insert(ToolDefinition definition) {
        toolDefinitionMapper.insert(definition);
        return definition;
    }

    /**
     * 更新工具定义。
     */
    public ToolDefinition update(ToolDefinition definition) {
        toolDefinitionMapper.updateById(definition);
        return definition;
    }

    /**
     * 删除工具定义。
     */
    public void deleteById(Long id) {
        toolDefinitionMapper.deleteById(id);
    }
}
