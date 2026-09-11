package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
