package com.mmcove.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mmcove.agent.common.model.entity.VibeRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * Vibe 任务执行实例 Mapper。
 */
@Mapper
public interface VibeRunMapper extends BaseMapper<VibeRun> {
}
