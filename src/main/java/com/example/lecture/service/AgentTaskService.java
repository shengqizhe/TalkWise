package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.dto.AgentTaskCreateRequest;
import com.example.lecture.entity.AgentTask;

public interface AgentTaskService {

    Page<AgentTask> page(Long userId, int current, int size, String type, String status);

    AgentTask get(Long userId, Long taskId);

    AgentTask create(Long userId, AgentTaskCreateRequest request);

    void cancel(Long userId, Long taskId);
}
