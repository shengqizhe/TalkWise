package com.example.lecture.agent.task;

import com.example.lecture.entity.AgentTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 任务执行器注册表，按任务类型分派执行器。 */
@Slf4j
@Component
public class AgentTaskExecutorRegistry {

    private final Map<String, AgentTaskExecutor> executors;

    public AgentTaskExecutorRegistry(List<AgentTaskExecutor> executorList) {
        this.executors = executorList.stream().collect(Collectors.toUnmodifiableMap(
                AgentTaskExecutor::getTaskType, Function.identity()));
    }

    public void execute(AgentTask task) {
        AgentTaskExecutor executor = executors.get(task.getType());
        if (executor == null) {
            throw new IllegalArgumentException("不支持的任务类型：" + task.getType());
        }
        executor.execute(task);
    }

    public boolean supports(String type) {
        return executors.containsKey(type);
    }
}
