package com.example.lecture.agent.task;

import com.example.lecture.entity.AgentTask;

/** 按任务类型执行主动任务。 */
public interface AgentTaskExecutor {

    String getTaskType();

    void execute(AgentTask task);
}
