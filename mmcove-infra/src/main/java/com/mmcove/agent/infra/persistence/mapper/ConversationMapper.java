package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
