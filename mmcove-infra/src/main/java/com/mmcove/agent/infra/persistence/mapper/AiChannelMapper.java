package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.AiChannel;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI渠道 Mapper。
 */
@Mapper
public interface AiChannelMapper extends BaseMapper<AiChannel> {
}
