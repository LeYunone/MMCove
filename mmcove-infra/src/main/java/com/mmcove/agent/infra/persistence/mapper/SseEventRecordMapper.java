package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.SseEventRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSE 事件记录 Mapper。
 */
@Mapper
public interface SseEventRecordMapper extends BaseMapper<SseEventRecord> {
}
