package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话消息 Mapper。
 */
@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
}
