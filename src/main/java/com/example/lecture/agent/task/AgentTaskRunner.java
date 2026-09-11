package com.example.lecture.agent.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.service.EvaluationAnalysisService;
import com.example.lecture.dto.WebSocketMessage;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.AgentTaskMapper;
import com.example.lecture.mapper.EvaluationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.service.WebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 异步任务执行器（任务型 Agent）：
 * 大数据量分析转到通用异步线程池执行，期间持续更新进度（可被对话查询），
 * 完成后通过 WebSocket 推送给发起用户（前端铃铛接收）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskRunner {

    private final AgentTaskMapper taskMapper;
    private final LectureMapper lectureMapper;
    private final EvaluationMapper evaluationMapper;
    private final EvaluationAnalysisService analysisService;
    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 评价分析任务：提交后立即返回，结果异步产出 */
    @Async("taskExecutor")
    public void runEvaluationAnalysis(Long taskId) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Agent 任务 {} 不存在，跳过", taskId);
            return;
        }
        try {
            updateProgress(taskId, 5, "开始分析");

            Long lectureId = objectMapper.readTree(task.getParams()).path("lectureId").asLong();
            Lecture lecture = lectureMapper.selectById(lectureId);
            if (lecture == null) {
                throw new IllegalStateException("讲座不存在（ID=" + lectureId + "）");
            }

            List<Evaluation> all = evaluationMapper.selectList(
                    new LambdaQueryWrapper<Evaluation>().eq(Evaluation::getLectureId, lectureId));
            EvaluationAnalysisService.PreparedData prepared = analysisService.preprocess(all);
            if (prepared.items().isEmpty()) {
                finish(taskId, "《" + lecture.getTitle() + "》共 " + prepared.totalCount()
                        + " 条评价，均为简短或重复内容，无需分析。");
                notifyUser(task.getUserId(), taskId, "《" + lecture.getTitle() + "》评价分析完成：无需要分析的内容。");
                return;
            }

            List<List<Evaluation>> batches = analysisService.packBatches(prepared.items());
            String report = analysisService.analyze(lecture, prepared, batches, (done, total) ->
                    updateProgress(taskId, 5 + (int) (done * 90.0 / total),
                            "正在分析第 " + done + "/" + total + " 批"));

            finish(taskId, report);
            notifyUser(task.getUserId(), taskId, "《" + lecture.getTitle() + "》评价分析完成，可以查看报告了。");
        } catch (Exception e) {
            log.error("Agent 任务执行失败: {}", taskId, e);
            fail(taskId, e.getMessage() == null ? "未知错误" : e.getMessage());
            notifyUser(task.getUserId(), taskId, "评价分析任务执行失败：" + e.getMessage());
        }
    }

    private void updateProgress(Long taskId, int progress, String text) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(AgentTask.STATUS_RUNNING);
        update.setProgress(Math.min(progress, 100));
        update.setProgressText(text);
        taskMapper.updateById(update);
    }

    private void finish(Long taskId, String report) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(AgentTask.STATUS_SUCCESS);
        update.setProgress(100);
        update.setProgressText("已完成");
        update.setResult(report);
        update.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void fail(Long taskId, String error) {
        AgentTask update = new AgentTask();
        update.setId(taskId);
        update.setStatus(AgentTask.STATUS_FAILED);
        update.setProgressText("已失败");
        update.setError(error != null && error.length() > 480 ? error.substring(0, 480) : error);
        update.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    /** 完成/失败通知：走现有 WebSocket 通道推给发起用户（前端铃铛接收） */
    private void notifyUser(Long userId, Long taskId, String content) {
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
}
