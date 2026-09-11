package com.example.lecture.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentProperties;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.ToolType;
import com.example.lecture.agent.service.EvaluationAnalysisService;
import com.example.lecture.agent.task.AgentTaskRunner;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.AgentTaskMapper;
import com.example.lecture.mapper.EvaluationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评价分析工具（只读）：
 * - 数据量小（批数 ≤ 阈值）→ 对话内同步分析，直接返回报告
 * - 数据量大（批数 > 阈值）→ 转后台任务，立即返回任务号，完成后 WebSocket 通知（可查进度）
 * 分析逻辑在 EvaluationAnalysisService，本类只做权限、规模判断与任务编排。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationAnalysisTools {

    private final LectureMapper lectureMapper;
    private final EvaluationMapper evaluationMapper;
    private final AgentRoleHelper roleHelper;
    private final AgentProperties properties;
    private final EvaluationAnalysisService analysisService;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskRunner taskRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AgentTool(
            name = "analyzeEvaluations",
            domain = "analysis",
            roles = {"admin", "teacher"},
            description = "分析指定讲座的学生评价：从评价中提炼主要问题、正面反馈与可行动的改进建议（按问题提及次数排序）。" +
                    "评价很多时自动分批，超过规模阈值会自动转为后台任务（返回任务号，完成后通知，可用 getAnalysisTaskStatus 查询进度）。" +
                    "适用：教师/管理员想了解某场讲座的口碑与改进方向。教师只能分析自己的讲座；lectureId 来自讲座列表。",
            type = ToolType.READ
    )
    public String analyzeEvaluations(
            @AgentParam(name = "lectureId", description = "讲座 ID（数字）") Long lectureId
    ) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "请先登录后再分析评价。";
        }
        Lecture lecture = lectureMapper.selectById(lectureId);
        if (lecture == null) {
            return "讲座不存在（ID=" + lectureId + "）。";
        }
        boolean admin = roleHelper.isAdmin(userId);
        if (!admin && !userId.equals(lecture.getOrganizerId())) {
            return "只能分析自己讲座的评价。";
        }

        List<Evaluation> all = evaluationMapper.selectList(
                new LambdaQueryWrapper<Evaluation>().eq(Evaluation::getLectureId, lectureId));
        if (all.isEmpty()) {
            return "《" + lecture.getTitle() + "》暂无学生评价，无需分析。";
        }

        EvaluationAnalysisService.PreparedData prepared = analysisService.preprocess(all);
        if (prepared.items().isEmpty()) {
            return "《" + lecture.getTitle() + "》共 " + prepared.totalCount() + " 条评价，"
                    + "均为简短或重复内容（" + prepared.shortCount() + " 条简短、"
                    + prepared.dupCount() + " 条重复），暂无需要深度分析的内容。";
        }
        List<List<Evaluation>> batches = analysisService.packBatches(prepared.items());

        // 小数据量：对话内同步分析；大数据量：转后台任务
        int threshold = properties.getAnalysis().getAsyncThresholdBatches();
        if (batches.size() <= threshold) {
            return analysisService.analyze(lecture, prepared, batches, null);
        }

        AgentTask task = new AgentTask();
        task.setUserId(userId);
        task.setType(AgentTask.TYPE_EVALUATION_ANALYSIS);
        ObjectNode params = objectMapper.createObjectNode();
        params.put("lectureId", lectureId);
        task.setParams(params.toString());
        task.setStatus(AgentTask.STATUS_PENDING);
        task.setProgress(0);
        task.setProgressText("排队中");
        taskMapper.insert(task);

        taskRunner.runEvaluationAnalysis(task.getId());

        return "《" + lecture.getTitle() + "》评价较多（有效 " + prepared.items().size()
                + " 条，分 " + batches.size() + " 批），已转为后台任务（任务号 " + task.getId() + "）。\n"
                + "我会在完成后通知你；你也可以随时问我\"分析进度\"来查看状态。";
    }

    @AgentTool(
            name = "getAnalysisTaskStatus",
            domain = "analysis",
            roles = {"admin", "teacher"},
            description = "查询评价分析任务的状态与结果。适用：用户问\"分析进度怎样\"\"报告好了吗\"\"上次那个分析\"。" +
                    "taskId 可以不传，默认查询当前用户最近一次任务；任务已完成时返回报告全文。"
    )
    public String getAnalysisTaskStatus(
            @AgentParam(name = "taskId", description = "任务 ID（数字），可不传（默认最近一次任务）", required = false) Long taskId
    ) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "请先登录后再查询任务。";
        }
        AgentTask task;
        if (taskId != null) {
            task = taskMapper.selectById(taskId);
            if (task == null) {
                return "任务不存在（ID=" + taskId + "）。";
            }
            if (!userId.equals(task.getUserId())) {
                return "只能查询自己发起的任务。";
            }
        } else {
            task = taskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                    .eq(AgentTask::getUserId, userId)
                    .orderByDesc(AgentTask::getCreatedTime)
                    .last("LIMIT 1"));
            if (task == null) {
                return "你还没有发起过评价分析任务。";
            }
        }

        return switch (task.getStatus() == null ? "" : task.getStatus()) {
            case AgentTask.STATUS_PENDING, AgentTask.STATUS_RUNNING ->
                    "任务 #" + task.getId() + " 进行中：" + task.getProgress() + "%"
                            + (task.getProgressText() == null ? "" : "（" + task.getProgressText() + "）")
                            + "。完成后我会通知你。";
            case AgentTask.STATUS_SUCCESS -> "任务 #" + task.getId() + " 已完成的报告如下：\n\n" + task.getResult();
            case AgentTask.STATUS_FAILED -> "任务 #" + task.getId() + " 执行失败："
                    + (task.getError() == null ? "未知原因" : task.getError()) + "。可以让我重新分析。";
            default -> "任务 #" + task.getId() + " 状态未知。";
        };
    }
}
