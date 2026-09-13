package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.dto.AgentTaskCreateRequest;
import com.example.lecture.entity.AgentTask;

public interface AgentTaskService {

    Page<AgentTask> page(Long userId, int current, int size, String type, String status);

    AgentTask get(Long userId, Long taskId);

    /**
     * 创建主动任务：只落库为 PENDING，由 {@code AgentTaskScheduler} 统一调度执行。
     * 携带 idempotencyKey 且命中已有任务时，直接返回该任务（不新建、不重复执行）。
     */
    AgentTask create(Long userId, AgentTaskCreateRequest request);

    void cancel(Long userId, Long taskId);

    /**
     * 重试失败任务：仅 FAILED 状态可用，清零重试计数与错误信息后重新排队。
     */
    AgentTask retry(Long userId, Long taskId);
}
