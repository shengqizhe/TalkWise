package com.example.lecture.agent.task;

import com.example.lecture.dto.WebSocketMessage;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.mapper.AgentTaskMapper;
import com.example.lecture.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 主动任务执行支撑：进度上报、成功终态、用户通知。
 *
 * <p>所有状态写入走条件 UPDATE（{@code WHERE status='RUNNING'}），
 * 因此被用户取消的任务不会被覆盖回 RUNNING/SUCCESS——取消检查点由 DB 层保证，
 * 执行器只需在关键节点调用 {@link #isActive(Long)} 决定是否提前收工。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskExecutionSupport {

    /** error 列 VARCHAR(500)，留出余量后截断 */
    static final int MAX_ERROR_LENGTH = 480;

    private final AgentTaskMapper taskMapper;
    private final WebSocketService webSocketService;

    /** 任务是否仍在执行中（未取消、未被删除）；false 时执行器应停止后续写入与通知 */
    public boolean isActive(Long taskId) {
        AgentTask current = taskMapper.selectById(taskId);
        return current != null && AgentTask.STATUS_RUNNING.equals(current.getStatus());
    }

    /** 上报进度；任务已非 RUNNING 时静默忽略 */
    public void updateProgress(Long taskId, int progress, String text) {
        taskMapper.updateProgress(taskId, Math.min(Math.max(progress, 0), 100), text, LocalDateTime.now());
    }

    /**
     * 写入成功终态。
     *
     * @return true 表示写入成功；false 表示任务已被取消（调用方不应再发完成通知）
     */
    public boolean succeed(Long taskId, String result) {
        return taskMapper.markSuccess(taskId, result, LocalDateTime.now()) == 1;
    }

    /** 完成/失败通知：走现有 WebSocket 通道推给发起用户（前端铃铛接收） */
    public void notifyUser(Long userId, Long taskId, String content) {
        if (userId == null) {
            return;
        }
        try {
            WebSocketMessage message = new WebSocketMessage();
            message.setType("AGENT_TASK");
            message.setReceiverId(userId);
            message.setContent(content);
            message.getData().put("taskId", taskId);
            webSocketService.sendToUser(message);
        } catch (Exception e) {
            log.warn("Agent 任务通知推送失败（taskId={}）：{}", taskId, e.getMessage());
        }
    }

    static String truncateError(String error) {
        String value = error == null || error.isBlank() ? "未知错误" : error;
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }
}
