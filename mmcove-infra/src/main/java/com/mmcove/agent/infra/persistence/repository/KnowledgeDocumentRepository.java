package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.KnowledgeDocument;
import com.mmcove.agent.infra.persistence.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识文档仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class KnowledgeDocumentRepository {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public List<KnowledgeDocument> findByKbId(Long kbId) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getKbId, kbId)
                .orderByDesc(KnowledgeDocument::getCreatedAt);
        return knowledgeDocumentMapper.selectList(wrapper);
    }

    public Optional<KnowledgeDocument> findById(Long id) {
        return Optional.ofNullable(knowledgeDocumentMapper.selectById(id));
    }

    public void insert(KnowledgeDocument doc) {
        knowledgeDocumentMapper.insert(doc);
    }

    public void updateStatus(Long id, int status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setStatus(status);
        knowledgeDocumentMapper.updateById(doc);
    }

    public boolean deleteById(Long id) {
        return knowledgeDocumentMapper.deleteById(id) > 0;
    }
}
