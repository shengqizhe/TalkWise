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
import java.util.List;

/**
 * 评价情感分析（单步 LLM 功能点 + 落库）：
 * 逐条评价输出情感分与改进建议，写入 evaluation 的空置字段 sentiment_score / improvement_suggestion。
 * 属 WRITE 工具：用户明确要求分析时由引擎直接执行，审计留痕。
 */
@Component
@RequiredArgsConstructor
public class EvaluationAnalysisTools {

    private final LectureMapper lectureMapper;
    private final EvaluationMapper evaluationMapper;
    private final AgentLlmClient llmClient;
    private final AgentRoleHelper roleHelper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AgentTool(
            name = "analyzeEvaluations",
            domain = "analysis",
            roles = {"admin", "teacher"},
            description = "分析指定讲座的学生评价：逐条给出情感分（0-100，越高越正面）与改进建议，并汇总正面/负面情况；" +
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

        List<Evaluation> evaluations = evaluationMapper.selectList(
                new LambdaQueryWrapper<Evaluation>().eq(Evaluation::getLectureId, lectureId));
        if (evaluations.isEmpty()) {
            return "《" + lecture.getTitle() + "》暂无学生评价，无需分析。";
        }

        // 构造批量分析请求：要求模型严格返回 JSON
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是讲座质量分析助手。下面是学生对讲座《").append(lecture.getTitle()).append("》的评价，")
                .append("请逐条分析情感倾向并给出改进建议。\n")
                .append("严格要求：只输出一个 JSON 对象，不要输出任何其他文字或 Markdown 标记，格式如下：\n")
                .append("{\"items\":[{\"index\":1,\"sentimentScore\":85,\"suggestion\":\"改进建议一句话\"}],\"summary\":\"整体评价摘要，80字以内\"}\n")
                .append("sentimentScore 为 0-100 的整数，分数越高越正面；suggestion 为针对该条评价的一句话改进建议。\n\n")
                .append("评价列表：\n");
        for (int i = 0; i < evaluations.size(); i++) {
            Evaluation ev = evaluations.get(i);
            prompt.append(i + 1).append(". 学生评分 ").append(ev.getScore())
                    .append("，评价内容：").append(ev.getContent() == null ? "（无文字）" : ev.getContent())
                    .append("\n");
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

        // 落库：情感分 + 改进建议
        int positive = 0, neutral = 0, negative = 0, saved = 0;
        double sum = 0;
        for (JsonNode item : root.get("items")) {
            int index = item.path("index").asInt(0);
            if (index < 1 || index > evaluations.size()) {
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
            Evaluation target = evaluations.get(index - 1);
            target.setSentimentScore(BigDecimal.valueOf(score));
            if (!suggestion.isBlank()) {
                target.setImprovementSuggestion(suggestion);
            }
            evaluationMapper.updateById(target);
            saved++;
        }

        String summary = root.path("summary").asText("");
        double avg = saved == 0 ? 0 : sum / saved;
        return "《" + lecture.getTitle() + "》评价分析完成（共 " + evaluations.size() + " 条，已保存 " + saved + " 条）：\n"
                + "- 情感分布：正面 " + positive + " 条 / 中性 " + neutral + " 条 / 负面 " + negative + " 条\n"
                + "- 平均情感分：" + String.format("%.1f", avg) + "（满分 100）\n"
                + (summary.isBlank() ? "" : "- 整体摘要：" + summary + "\n")
                + "改进建议已写入各条评价记录。";
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
