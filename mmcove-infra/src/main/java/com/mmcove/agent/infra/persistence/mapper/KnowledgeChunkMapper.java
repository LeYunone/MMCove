package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识切片 Mapper。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}
