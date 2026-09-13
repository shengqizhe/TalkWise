package com.example.lecture.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.agent.task.AgentTaskExecutorRegistry;
import com.example.lecture.agent.task.AgentTaskIdempotency;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.dto.AgentTaskCreateRequest;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.mapper.AgentTaskMapper;
import com.example.lecture.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
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

        String idempotencyKey = AgentTaskIdempotency.resolve(userId, request.getIdempotencyKey());
        if (idempotencyKey != null) {
            AgentTask existing = findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                log.info("幂等命中，返回已有任务 {}（key={}）", existing.getId(), idempotencyKey);
                return existing;
            }
        }

        AgentTask task = new AgentTask();
        task.setUserId(userId);
        task.setType(request.getType());
        task.setIdempotencyKey(idempotencyKey);
        task.setName(StringUtils.hasText(request.getName()) ? request.getName() : request.getType());
        task.setParams(request.getParams());
        task.setStatus(AgentTask.STATUS_PENDING);
        task.setProgress(0);
        task.setProgressText("排队中");
        task.setPriority(normalizePriority(request.getPriority()));
        task.setRetryCount(0);
        task.setMaxRetries(normalizeMaxRetries(request.getMaxRetries()));
        task.setCreatedTime(LocalDateTime.now());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 并发同键提交：唯一索引兜底，回查并返回先落库的那条
            AgentTask existing = idempotencyKey == null ? null : findByIdempotencyKey(idempotencyKey);
            if (existing == null) {
                throw e;
            }
            log.info("并发幂等命中，返回已有任务 {}（key={}）", existing.getId(), idempotencyKey);
            return existing;
        }
        // 不在此处提交执行：统一由 AgentTaskScheduler 轮询抢占后执行
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

    @Override
    public AgentTask retry(Long userId, Long taskId) {
        AgentTask task = get(userId, taskId);
        if (!AgentTask.STATUS_FAILED.equals(task.getStatus())) {
            throw new ApiException("只有执行失败的任务可以重试");
        }
        // 条件 UPDATE（FAILED -> PENDING）：与调度器/取消操作的并发写互相隔离
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, taskId)
                .eq(AgentTask::getStatus, AgentTask.STATUS_FAILED)
                .set(AgentTask::getStatus, AgentTask.STATUS_PENDING)
                .set(AgentTask::getRetryCount, 0)
                .set(AgentTask::getNextRetryTime, null)
                .set(AgentTask::getError, null)
                .set(AgentTask::getProgress, 0)
                .set(AgentTask::getProgressText, "重新排队中")
                .set(AgentTask::getStartedTime, null)
                .set(AgentTask::getFinishedTime, null));
        if (updated != 1) {
            throw new ApiException("任务状态已变化，请刷新后重试");
        }
        return taskMapper.selectById(taskId);
    }

    private AgentTask findByIdempotencyKey(String idempotencyKey) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private int normalizePriority(Integer priority) {
        return priority == null ? AgentTask.DEFAULT_PRIORITY : priority;
    }

    private int normalizeMaxRetries(Integer maxRetries) {
        if (maxRetries == null) {
            return AgentTask.DEFAULT_MAX_RETRIES;
        }
        return Math.min(Math.max(maxRetries, 0), AgentTask.MAX_RETRIES_LIMIT);
    }

    private void checkOwner(AgentTask task, Long userId) {
        if (task == null) throw new ApiException("任务不存在");
        if (!userId.equals(task.getUserId()) && !StpUtil.hasRole("admin")) {
            throw new ApiException("无权访问该任务");
        }
    }
}
