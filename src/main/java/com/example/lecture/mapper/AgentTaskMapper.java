package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 异步任务 Mapper
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
