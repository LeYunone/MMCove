package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.KnowledgeChunk;
import com.mmcove.agent.infra.persistence.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识切片仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkRepository {

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public List<KnowledgeChunk> findByDocId(Long docId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getDocId, docId).orderByAsc(KnowledgeChunk::getSeq);
        return knowledgeChunkMapper.selectList(wrapper);
    }

    /** 检索回表:Milvus 召回 vectorId → 查切片原文(顺序由调用方按召回相关度重排) */
    public List<KnowledgeChunk> findByVectorIds(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KnowledgeChunk::getVectorId, vectorIds);
        return knowledgeChunkMapper.selectList(wrapper);
    }

    public void insert(KnowledgeChunk chunk) {
        knowledgeChunkMapper.insert(chunk);
    }

    public int deleteByDocId(Long docId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getDocId, docId);
        return knowledgeChunkMapper.delete(wrapper);
    }
}
