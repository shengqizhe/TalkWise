package com.example.lecture.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentLlmClient;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.ToolType;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.EvaluationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 评价情感分析（单步 LLM 功能点 + 落库）：
 * 选取评分最高/最低各若干条评价，去重后**打乱顺序、匿名**（不向模型暴露来源分组与原始评分）交给模型，
 * 让模型仅依据文字内容独立判断情感；结果写入 evaluation 的 sentiment_score / improvement_suggestion 字段。
 * 属 WRITE 工具：用户明确要求分析时由引擎直接执行，审计留痕。
 */
@Component
@RequiredArgsConstructor
public class EvaluationAnalysisTools {

    /** 两端各取多少条（信息量最大的"最好 + 最差"评价） */
    private static final int EXTREME_N = 10;

    private final LectureMapper lectureMapper;
    private final EvaluationMapper evaluationMapper;
    private final AgentLlmClient llmClient;
    private final AgentRoleHelper roleHelper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AgentTool(
            name = "analyzeEvaluations",
            domain = "analysis",
            roles = {"admin", "teacher"},
            description = "分析指定讲座的学生评价：给出情感分（0-100，越高越正面）与改进建议，并汇总正面/负面情况；" +
                    "分析结果会保存到对应评价记录。适用：教师/管理员想了解某场讲座的口碑反馈。教师只能分析自己的讲座。",
            type = ToolType.WRITE
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

        // 选取两端评价 + 去重 + 打乱（匿名混合，不给模型任何分组或评分线索）
        List<Evaluation> selected = selectExtremes(all);

        // 构造批量分析请求：仅给编号与文字内容，严格 JSON 输出
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是讲座质量分析助手。下面是学生对讲座《").append(lecture.getTitle()).append("》的评价（顺序已打乱），")
                .append("请逐条分析文字内容的情感倾向并给出改进建议。\n")
                .append("严格要求：只输出一个 JSON 对象，不要输出任何其他文字或 Markdown 标记，格式如下：\n")
                .append("{\"items\":[{\"index\":1,\"sentimentScore\":85,\"suggestion\":\"改进建议一句话\"}],\"summary\":\"整体评价摘要，80字以内\"}\n")
                .append("sentimentScore 为 0-100 的整数（基于评价文字判断，分数越高越正面）；suggestion 为针对该条评价的一句话改进建议。\n\n")
                .append("评价列表：\n");
        for (int i = 0; i < selected.size(); i++) {
            Evaluation ev = selected.get(i);
            String content = ev.getContent() == null || ev.getContent().isBlank() ? "（无文字评价）" : ev.getContent().trim();
            prompt.append(i + 1).append(". ").append(content).append("\n");
        }

        String raw;
        try {
            raw = llmClient.generateText(prompt.toString());
        } catch (Exception e) {
            return "评价分析失败（模型调用异常）：" + e.getMessage() + "。";
        }
        JsonNode root = parseJson(raw);
        if (root == null || !root.has("items") || !root.get("items").isArray()) {
            return "评价分析未成功：模型返回格式异常，请重试。";
        }

        // 落库：情感分 + 改进建议（按 index 映射回打乱后的选取列表）
        int positive = 0, neutral = 0, negative = 0, saved = 0;
        double sum = 0;
        for (JsonNode item : root.get("items")) {
            int index = item.path("index").asInt(0);
            if (index < 1 || index > selected.size()) {
                continue;
            }
            int score = Math.max(0, Math.min(100, item.path("sentimentScore").asInt(50)));
            String suggestion = item.path("suggestion").asText("");
            if (score >= 70) {
                positive++;
            } else if (score >= 40) {
                neutral++;
            } else {
                negative++;
            }
            sum += score;
            Evaluation target = selected.get(index - 1);
            target.setSentimentScore(BigDecimal.valueOf(score));
            if (!suggestion.isBlank()) {
                target.setImprovementSuggestion(suggestion);
            }
            evaluationMapper.updateById(target);
            saved++;
        }

        String summary = root.path("summary").asText("");
        double avg = saved == 0 ? 0 : sum / saved;
        return "《" + lecture.getTitle() + "》评价分析完成："
                + "共 " + all.size() + " 条评价，选取评分两端共 " + selected.size() + " 条（匿名混合）进行分析，已保存 " + saved + " 条。\n"
                + "- 情感分布：正面 " + positive + " 条 / 中性 " + neutral + " 条 / 负面 " + negative + " 条\n"
                + "- 平均情感分：" + String.format("%.1f", avg) + "（满分 100）\n"
                + (summary.isBlank() ? "" : "- 整体摘要：" + summary + "\n")
                + "改进建议已写入对应评价记录。";
    }

    /**
     * 选取评分最高与最低各 EXTREME_N 条：
     * ① 按记录 id 去重（两端可能重叠）；② 按内容去重（忽略空白差异，空内容各自保留）；
     * ③ 打乱顺序——不向模型暴露哪些来自高分端、哪些来自低分端。
     */
    private List<Evaluation> selectExtremes(List<Evaluation> all) {
        List<Evaluation> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(Evaluation::getScore, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Evaluation::getId));
        int n = Math.min(EXTREME_N, sorted.size());
        List<Evaluation> bottom = sorted.subList(0, n);                                   // 最低分端
        List<Evaluation> top = sorted.subList(sorted.size() - n, sorted.size());          // 最高分端

        Map<Long, Evaluation> byId = new LinkedHashMap<>();
        top.forEach(ev -> byId.put(ev.getId(), ev));
        bottom.forEach(ev -> byId.putIfAbsent(ev.getId(), ev));

        Set<String> seenContent = new HashSet<>();
        List<Evaluation> deduped = new ArrayList<>();
        for (Evaluation ev : byId.values()) {
            String normalized = ev.getContent() == null ? "" : ev.getContent().replaceAll("\\s+", "");
            if (seenContent.add(normalized)) {
                deduped.add(ev);
            }
        }
        java.util.Collections.shuffle(deduped, new Random());
        return deduped;
    }

    /** 容错解析：截取第一个 { 到最后一个 }，兼容模型偶尔包裹的说明文字 */
    private JsonNode parseJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
