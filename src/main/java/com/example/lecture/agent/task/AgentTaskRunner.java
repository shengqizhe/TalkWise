package com.example.lecture.agent.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.service.EvaluationAnalysisService;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.EvaluationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评价分析任务执行器（任务型 Agent）：
 * 大数据量分析由 {@link AgentTaskScheduler} 抢占后提交线程池执行，期间持续更新进度（可被对话查询），
 * 完成后通过 WebSocket 推送给发起用户（前端铃铛接收）。
 *
 * <p>失败处理：本类不再自行把任务置为 FAILED，而是把异常上抛给调度器，
 * 由调度器按 retryCount/maxRetries 决定退避重排还是判失败——避免"立即失败"绕过重试策略。
 * 被用户取消的任务在取消检查点直接返回（不抛异常），因此不会被重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskRunner implements AgentTaskExecutor {

    private final LectureMapper lectureMapper;
    private final EvaluationMapper evaluationMapper;
    private final EvaluationAnalysisService analysisService;
    private final AgentTaskExecutionSupport support;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getTaskType() {
        return AgentTask.TYPE_EVALUATION_ANALYSIS;
    }

    @Override
    public void execute(AgentTask task) {
        Long taskId = task.getId();
        if (!support.isActive(taskId)) {
            log.info("Agent 任务 {} 已取消或不存在，跳过执行", taskId);
            return;
        }
        runEvaluationAnalysis(task);
    }

    private void runEvaluationAnalysis(AgentTask task) {
        Long taskId = task.getId();
        support.updateProgress(taskId, 5, "开始分析");

        Long lectureId = readLectureId(task);
        Lecture lecture = lectureMapper.selectById(lectureId);
        if (lecture == null) {
            throw new IllegalStateException("讲座不存在（ID=" + lectureId + "）");
        }

        List<Evaluation> all = evaluationMapper.selectList(
                new LambdaQueryWrapper<Evaluation>().eq(Evaluation::getLectureId, lectureId));
        EvaluationAnalysisService.PreparedData prepared = analysisService.preprocess(all);
        if (prepared.items().isEmpty()) {
            String summary = "《" + lecture.getTitle() + "》共 " + prepared.totalCount()
                    + " 条评价，均为简短或重复内容，无需分析。";
            if (support.succeed(taskId, summary)) {
                support.notifyUser(task.getUserId(), taskId,
                        "《" + lecture.getTitle() + "》评价分析完成：无需要分析的内容。");
            }
            return;
        }

        List<List<Evaluation>> batches = analysisService.packBatches(prepared.items());
        String report = analysisService.analyze(lecture, prepared, batches, (done, total) -> {
            if (support.isActive(taskId)) {
                support.updateProgress(taskId, 5 + (int) (done * 90.0 / total),
                        "正在分析第 " + done + "/" + total + " 批");
            }
        });

        // 取消检查点：用户已取消则丢弃结果，不入库、不通知（也不触发重试）
        if (!support.isActive(taskId)) {
            log.info("Agent 任务 {} 在分析过程中被取消，丢弃结果", taskId);
            return;
        }
        if (support.succeed(taskId, report)) {
            support.notifyUser(task.getUserId(), taskId, "《" + lecture.getTitle() + "》评价分析完成，可以查看报告了。");
        }
    }

    private Long readLectureId(AgentTask task) {
        try {
            long lectureId = objectMapper.readTree(task.getParams() == null ? "{}" : task.getParams())
                    .path("lectureId").asLong();
            if (lectureId <= 0) {
                throw new IllegalStateException("任务参数缺少 lectureId");
            }
            return lectureId;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("任务参数解析失败：" + e.getMessage(), e);
        }
    }
}
