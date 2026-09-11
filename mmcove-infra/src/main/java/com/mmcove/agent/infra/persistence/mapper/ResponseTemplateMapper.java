package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ResponseTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 响应格式模板 Mapper。
 */
@Mapper
public interface ResponseTemplateMapper extends BaseMapper<ResponseTemplate> {
}
