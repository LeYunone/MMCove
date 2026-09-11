package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ToolDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具定义 Mapper。
 */
@Mapper
public interface ToolDefinitionMapper extends BaseMapper<ToolDefinition> {
}
