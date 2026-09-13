package com.example.lecture.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.agent.task.AgentTaskExecutorRegistry;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.dto.AgentTaskCreateRequest;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.mapper.AgentTaskMapper;
import com.example.lecture.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentTaskServiceImpl implements AgentTaskService {

    private final AgentTaskMapper taskMapper;
    private final AgentTaskExecutorRegistry executorRegistry;

    @Override
    public Page<AgentTask> page(Long userId, int current, int size, String type, String status) {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .orderByDesc(AgentTask::getCreatedTime);
        if (StringUtils.hasText(type)) wrapper.eq(AgentTask::getType, type);
        if (StringUtils.hasText(status)) wrapper.eq(AgentTask::getStatus, status);
        return taskMapper.selectPage(new Page<>(current, Math.min(size, 100)), wrapper);
    }

    @Override
    public AgentTask get(Long userId, Long taskId) {
        AgentTask task = taskMapper.selectById(taskId);
        checkOwner(task, userId);
        return task;
    }

    @Override
    public AgentTask create(Long userId, AgentTaskCreateRequest request) {
        if (!executorRegistry.supports(request.getType())) {
            throw new ApiException("不支持的任务类型：" + request.getType());
        }
        AgentTask task = new AgentTask();
        task.setUserId(userId);
        task.setType(request.getType());
        task.setName(StringUtils.hasText(request.getName()) ? request.getName() : request.getType());
        task.setParams(request.getParams());
        task.setStatus(AgentTask.STATUS_PENDING);
        task.setProgress(0);
        task.setProgressText("排队中");
        task.setPriority(0);
        task.setCreatedTime(LocalDateTime.now());
        taskMapper.insert(task);
        executorRegistry.execute(task);
        return task;
    }

    @Override
    public void cancel(Long userId, Long taskId) {
        AgentTask task = get(userId, taskId);
        if (!AgentTask.STATUS_PENDING.equals(task.getStatus()) && !AgentTask.STATUS_RUNNING.equals(task.getStatus())) {
            throw new ApiException("当前任务状态不可取消");
        }
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(AgentTask.STATUS_CANCELLED);
        update.setProgressText("已取消");
        update.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void checkOwner(AgentTask task, Long userId) {
        if (task == null) throw new ApiException("任务不存在");
        if (!userId.equals(task.getUserId()) && !StpUtil.hasRole("admin")) {
            throw new ApiException("无权访问该任务");
        }
    }
}
