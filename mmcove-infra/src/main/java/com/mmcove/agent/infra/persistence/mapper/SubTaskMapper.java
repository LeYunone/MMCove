package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.SubTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 子任务 Mapper。
 */
@Mapper
public interface SubTaskMapper extends BaseMapper<SubTask> {
}
