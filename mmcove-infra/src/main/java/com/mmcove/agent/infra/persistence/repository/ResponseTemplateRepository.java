package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.entity.ResponseTemplate;
import com.mmcove.agent.infra.persistence.mapper.ResponseTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 响应格式模板增删改查仓库。
 */
@Repository
@RequiredArgsConstructor
public class ResponseTemplateRepository {

    private final ResponseTemplateMapper responseTemplateMapper;

    /**
     * 查询所有活跃模板，按优先级降序。
     */
    public List<ResponseTemplate> findAllActive() {
        LambdaQueryWrapper<ResponseTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResponseTemplate::getStatus, "ACTIVE")
                .orderByDesc(ResponseTemplate::getPriority)
                .orderByAsc(ResponseTemplate::getId);
        return responseTemplateMapper.selectList(wrapper);
    }

    /**
     * 分页查询模板。
     */
    public Page<ResponseTemplate> findPage(int pageNum, int pageSize, String scope, String keyword) {
        LambdaQueryWrapper<ResponseTemplate> wrapper = new LambdaQueryWrapper<>();
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(ResponseTemplate::getScope, scope);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(ResponseTemplate::getTemplateName, keyword)
                    .or().like(ResponseTemplate::getTemplateCode, keyword));
        }
        wrapper.orderByDesc(ResponseTemplate::getPriority)
                .orderByAsc(ResponseTemplate::getId);
        return responseTemplateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 根据 ID 查询。
     */
    public Optional<ResponseTemplate> findById(Long id) {
        return Optional.ofNullable(responseTemplateMapper.selectById(id));
    }

    /**
     * 根据 templateCode 查询。
     */
    public Optional<ResponseTemplate> findByTemplateCode(String templateCode) {
        LambdaQueryWrapper<ResponseTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResponseTemplate::getTemplateCode, templateCode);
        return Optional.ofNullable(responseTemplateMapper.selectOne(wrapper));
    }

    /**
     * 新增模板。
     */
    public ResponseTemplate insert(ResponseTemplate template) {
        responseTemplateMapper.insert(template);
        return template;
    }

    /**
     * 更新模板。
     */
    public ResponseTemplate update(ResponseTemplate template) {
        responseTemplateMapper.updateById(template);
        return template;
    }

    /**
     * 删除模板。
     */
    public void deleteById(Long id) {
        responseTemplateMapper.deleteById(id);
    }
}
