package com.example.lecture.agent.task;

import com.example.lecture.agent.tool.StatisticsTools;
import com.example.lecture.entity.AgentTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 报表生成任务执行器（type=report_generation）。
 *
 * <p>复用 {@link StatisticsTools} 的 metric 聚合逻辑（不在执行器里重写统计口径），
 * 将选定的指标拼接为报表文本写入任务的 result，完成后经 WebSocket 通知发起用户。
 *
 * <p>params 约定（JSON，均可省略）：
 * <pre>{"title":"9 月运营周报","metrics":["overview","top_lectures"]}</pre>
 * metrics 省略时输出全部指标 overview、categories、departments、top_lectures；
 * 非法 metric 会被忽略，若全部非法则报错交由调度器按重试策略处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationTaskExecutor implements AgentTaskExecutor {

    /** 支持的指标，顺序即报表中的展示顺序 */
    static final List<String> SUPPORTED_METRICS = List.of("overview", "categories", "departments", "top_lectures");

    /** 报表标题列宽上限，防止异常入参写出超长标题 */
    static final int MAX_TITLE_LENGTH = 120;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StatisticsTools statisticsTools;
    private final AgentTaskExecutionSupport support;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getTaskType() {
        return AgentTask.TYPE_REPORT_GENERATION;
    }

    @Override
    public void execute(AgentTask task) {
        Long taskId = task.getId();
        if (!support.isActive(taskId)) {
            log.info("报表任务 {} 已取消或不存在，跳过执行", taskId);
            return;
        }
        support.updateProgress(taskId, 10, "解析报表参数");

        JsonNode params = readParams(task);
        String title = resolveTitle(params, task);
        List<String> metrics = resolveMetrics(params);

        Long userId = task.getUserId();
        StringBuilder report = new StringBuilder();
        report.append("# ").append(title).append("\n")
                .append("生成时间：").append(TIME_FMT.format(LocalDateTime.now())).append("\n\n");

        int done = 0;
        for (String metric : metrics) {
            if (!support.isActive(taskId)) {
                log.info("报表任务 {} 在执行中被取消，丢弃结果", taskId);
                return;
            }
            String section = statisticsTools.queryStatistics(metric, userId);
            report.append("## ").append(metricLabel(metric)).append("\n")
                    .append(section).append("\n\n");
            done++;
            support.updateProgress(taskId, 10 + (int) (done * 85.0 / metrics.size()), "已生成 " + done + "/" + metrics.size() + " 个指标");
        }

        String content = report.toString().strip();
        if (support.succeed(taskId, content)) {
            support.notifyUser(userId, taskId, title + " 已生成，可以查看了。");
        }
    }

    /** 解析 params；为空或非法 JSON 时按空参数处理（走默认指标） */
    private JsonNode readParams(AgentTask task) {
        String raw = task.getParams();
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node == null || node.isNull() ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            throw new IllegalStateException("任务参数解析失败：" + e.getMessage(), e);
        }
    }

    private String resolveTitle(JsonNode params, AgentTask task) {
        String raw = params.path("title").asText(null);
        if (raw == null || raw.isBlank()) {
            raw = task.getName() == null || task.getName().isBlank() ? "平台数据报表" : task.getName();
        }
        String trimmed = raw.trim();
        return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
    }

    /**
     * 解析指标列表：去重、按 {@link #SUPPORTED_METRICS} 顺序、剔除不支持项；
     * 未指定时返回全部指标。
     */
    private List<String> resolveMetrics(JsonNode params) {
        JsonNode metricsNode = params.path("metrics");
        Set<String> requested = new LinkedHashSet<>();
        if (metricsNode.isArray()) {
            metricsNode.forEach(node -> {
                String value = node.asText("").trim().toLowerCase();
                if (SUPPORTED_METRICS.contains(value)) {
                    requested.add(value);
                }
            });
        } else if (metricsNode.isTextual()) {
            String value = metricsNode.asText().trim().toLowerCase();
            if (SUPPORTED_METRICS.contains(value)) {
                requested.add(value);
            }
        }
        if (requested.isEmpty()) {
            return SUPPORTED_METRICS;
        }
        List<String> ordered = new ArrayList<>(SUPPORTED_METRICS);
        ordered.retainAll(requested);
        return ordered;
    }

    private String metricLabel(String metric) {
        return switch (metric) {
            case "overview" -> "总体概览";
            case "categories" -> "分类热度";
            case "departments" -> "院系分布";
            case "top_lectures" -> "热门讲座";
            default -> metric;
        };
    }
}
