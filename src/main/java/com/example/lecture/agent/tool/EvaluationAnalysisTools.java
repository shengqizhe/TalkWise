package com.example.lecture.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentLlmClient;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentProperties;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.ToolType;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.EvaluationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 评价分析（多批 Map-Reduce，只读）：
 *
 * 解决"评论太多、上下文太长"：预处理（内容去重 + 短评过滤）→ 按「token 预算 + 条数安全阀」分批（bin packing）
 * → Map 阶段每批提炼结构化产物（问题/亮点/建议，限长）→ Reduce 阶段合并（输入超预算自动两级 Reduce）。
 *
 * 参数全部来自配置（agent.analysis.*，标定值）：一级参数是每批内容预算（token 估算），
 * 条数只是防碎片化的安全阀；运行时"装填"是动态的（按实际内容量装批）。
 * 产出为分析报告文本（不落库）；情感分已按产品决策移除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationAnalysisTools {

    /** Reduce 递归深度上限（防极端输入下的无限递归） */
    private static final int MAX_REDUCE_DEPTH = 3;

    private final LectureMapper lectureMapper;
    private final EvaluationMapper evaluationMapper;
    private final AgentLlmClient llmClient;
    private final AgentRoleHelper roleHelper;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AgentTool(
            name = "analyzeEvaluations",
            domain = "analysis",
            roles = {"admin", "teacher"},
            description = "分析指定讲座的学生评价：从评价中提炼主要问题、正面反馈与可行动的改进建议（评价很多时自动分批处理，按问题提及次数排序）。" +
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

        // ① 预处理：内容去重 + 短评过滤
        AgentProperties.Analysis cfg = properties.getAnalysis();
        List<Evaluation> prepared = new ArrayList<>();
        Set<String> seenContent = new HashSet<>();
        int dupCount = 0;
        int shortCount = 0;
        for (Evaluation ev : all) {
            String content = ev.getContent() == null ? "" : ev.getContent().trim();
            if (content.length() < Math.max(1, cfg.getMinContentLength())) {
                shortCount++;
                continue;
            }
            String normalized = content.replaceAll("\\s+", "");
            if (!seenContent.add(normalized)) {
                dupCount++;
                continue;
            }
            prepared.add(ev);
        }
        if (prepared.isEmpty()) {
            return "《" + lecture.getTitle() + "》共 " + all.size() + " 条评价，"
                    + "均为简短评价或重复内容（" + shortCount + " 条简短、" + dupCount + " 条重复），暂无需要深度分析的内容。";
        }

        // ② 分批：token 预算为主约束，条数为安全阀（bin packing，运行时按内容量动态装批）
        List<List<Evaluation>> batches = packBatches(prepared, cfg.getBatchMaxTokens(), cfg.getBatchMaxItems());

        // ③ Map：逐批提炼结构化产物（单批失败重试一次，仍失败则跳过并计数）
        List<JsonNode> batchResults = new ArrayList<>();
        int failedBatches = 0;
        for (List<Evaluation> batch : batches) {
            JsonNode result = callMap(batch, lecture.getTitle());
            if (result == null) {
                failedBatches++;
            } else {
                batchResults.add(result);
            }
        }
        if (batchResults.isEmpty()) {
            return "评价分析失败：全部批次未能得到有效结果，请稍后重试。";
        }

        // ④ Reduce：合并各批产物（输入超预算自动两级 Reduce）
        JsonNode report = reduceTree(batchResults, lecture.getTitle(), 0);
        if (report == null) {
            return "评价分析失败：汇总阶段未能得到有效结果，请稍后重试。";
        }

        // ⑤ 格式化报告
        return formatReport(lecture.getTitle(), all.size(), dupCount, shortCount,
                batches.size(), failedBatches, report);
    }

    // ==================== 分批（bin packing） ====================

    private List<List<Evaluation>> packBatches(List<Evaluation> items, int budgetTokens, int maxItems) {
        List<List<Evaluation>> batches = new ArrayList<>();
        List<Evaluation> current = new ArrayList<>();
        int currentTokens = 0;
        for (Evaluation ev : items) {
            int cost = estimateTokens(ev.getContent()) + 12; // 编号与格式开销
            boolean full = !current.isEmpty() && (current.size() >= maxItems || currentTokens + cost > budgetTokens);
            if (full) {
                batches.add(current);
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(ev);
            currentTokens += cost;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /**
     * token 估算器：中文场景按 字符数×0.75 近似（略偏安全）。
     * 如需精确可替换为对应模型的 tokenizer——这是评测标定的接入点之一。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() * 0.75);
    }

    // ==================== Map / Reduce ====================

    /** Map：提炼单批评价（重试一次；解析失败返回 null） */
    private JsonNode callMap(List<Evaluation> batch, String title) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是讲座质量分析助手。下面是学生对讲座《").append(title)
                .append("》的一批评价（顺序无意义）。请提炼本批内容，只输出一个 JSON 对象，不要输出任何其他文字：\n")
                .append("{\"issues\":[{\"type\":\"问题归类（≤8字）\",\"count\":提及该问题的评价条数,\"samples\":[\"代表性原文摘录（≤20字）\"]}],")
                .append("\"praises\":[{\"type\":\"亮点归类（≤8字）\",\"count\":条数,\"samples\":[\"摘录\"]}],")
                .append("\"suggestions\":[\"可行动的改进建议（每条≤30字）\"]}\n")
                .append("要求：同类问题必须合并计数；没有内容则给空数组；samples 最多 2 条。\n\n评价列表：\n");
        for (int i = 0; i < batch.size(); i++) {
            prompt.append(i + 1).append(". ").append(batch.get(i).getContent().trim()).append("\n");
        }
        String text = prompt.toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode node = parseJson(llmClient.generateText(text));
                if (node != null && node.has("issues")) {
                    return node;
                }
            } catch (Exception e) {
                log.warn("评价分析 Map 批次调用异常（第 {} 次）: {}", attempt + 1, e.getMessage());
            }
        }
        return null;
    }

    /** Reduce 树：输入在预算内则一次合并；超出则分组先合并再递归（最多 MAX_REDUCE_DEPTH 层） */
    private JsonNode reduceTree(List<JsonNode> inputs, String title, int depth) {
        String payload = toJson(inputs);
        int budget = properties.getAnalysis().getReduceInputBudgetTokens();
        if (estimateTokens(payload) <= budget || depth >= MAX_REDUCE_DEPTH) {
            return callReduce(inputs, title);
        }
        // 分组：按预算切分
        List<List<JsonNode>> groups = new ArrayList<>();
        List<JsonNode> current = new ArrayList<>();
        int currentTokens = 0;
        for (JsonNode node : inputs) {
            int cost = estimateTokens(node.toString());
            if (!current.isEmpty() && currentTokens + cost > budget) {
                groups.add(current);
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(node);
            currentTokens += cost;
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        List<JsonNode> partials = new ArrayList<>();
        for (List<JsonNode> group : groups) {
            JsonNode partial = callReduce(group, title);
            if (partial != null) {
                partials.add(partial);
            }
        }
        if (partials.isEmpty()) {
            return null;
        }
        log.info("评价分析触发两级 Reduce：{} 组 → 递归第 {} 层", groups.size(), depth + 1);
        return reduceTree(partials, title, depth + 1);
    }

    /** Reduce：合并一批结构化产物（重试一次） */
    private JsonNode callReduce(List<JsonNode> inputs, String title) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是讲座质量分析助手。下面是同一场讲座《").append(title)
                .append("》各批次评价的汇总提炼结果（JSON 数组，每项来自一批评价）。")
                .append("请跨批次合并同类项，只输出一个 JSON 对象，不要输出任何其他文字：\n")
                .append("{\"issues\":[{\"type\":\"\",\"count\":合计提及条数,\"severity\":\"高/中/低\",\"samples\":[\"≤20字\"],\"suggestions\":[\"针对该问题的改进建议（≤30字）\"]}],")
                .append("\"praises\":[{\"type\":\"\",\"count\":合计条数,\"samples\":[\"≤20字\"]}],")
                .append("\"overall\":\"整体评估（100字以内）\"}\n")
                .append("要求：issues 按合计提及条数从多到少排列且最多 8 项；samples 每项最多 2 条。\n\n各批结果：\n")
                .append(toJson(inputs));
        String text = prompt.toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode node = parseJson(llmClient.generateText(text));
                if (node != null && node.has("issues")) {
                    return node;
                }
            } catch (Exception e) {
                log.warn("评价分析 Reduce 调用异常（第 {} 次）: {}", attempt + 1, e.getMessage());
            }
        }
        return null;
    }

    // ==================== 格式化 ====================

    private String formatReport(String title, int total, int dupCount, int shortCount,
                                int batchCount, int failedBatches, JsonNode report) {
        StringBuilder sb = new StringBuilder();
        sb.append("《").append(title).append("》评价分析报告\n");
        sb.append("· 数据口径：共 ").append(total).append(" 条评价，内容去重合并 ").append(dupCount)
                .append(" 条，简短评价 ").append(shortCount).append(" 条（仅计数未分析）")
                .append("，实际分析 ").append(batchCount - failedBatches).append(" 批");
        if (failedBatches > 0) {
            sb.append("（另有 ").append(failedBatches).append(" 批分析失败已跳过）");
        }
        sb.append("\n");

        JsonNode issues = report.path("issues");
        if (issues.isArray() && issues.size() > 0) {
            sb.append("\n主要问题（按提及次数）：\n");
            int idx = 1;
            for (JsonNode issue : issues) {
                sb.append(idx++).append(". ").append(issue.path("type").asText("未分类"))
                        .append("（提及 ").append(issue.path("count").asInt(0)).append(" 次");
                String severity = issue.path("severity").asText("");
                if (!severity.isBlank()) {
                    sb.append("，严重度 ").append(severity);
                }
                sb.append("）");
                appendSamples(sb, issue.path("samples"));
                sb.append("\n");
                JsonNode suggestions = issue.path("suggestions");
                if (suggestions.isArray()) {
                    for (JsonNode s : suggestions) {
                        sb.append("   → ").append(s.asText()).append("\n");
                    }
                }
            }
        }

        JsonNode praises = report.path("praises");
        if (praises.isArray() && praises.size() > 0) {
            sb.append("\n正面反馈：\n");
            for (JsonNode praise : praises) {
                sb.append("- ").append(praise.path("type").asText(""))
                        .append("（").append(praise.path("count").asInt(0)).append(" 次）");
                appendSamples(sb, praise.path("samples"));
                sb.append("\n");
            }
        }

        String overall = report.path("overall").asText("");
        if (!overall.isBlank()) {
            sb.append("\n整体评估：").append(overall);
        }
        return sb.toString().trim();
    }

    private void appendSamples(StringBuilder sb, JsonNode samples) {
        if (samples.isArray() && samples.size() > 0) {
            sb.append("，如\u201c").append(samples.get(0).asText()).append("\u201d");
        }
    }

    // ==================== 工具 ====================

    /** 容错解析：截取第一个 { 到最后一个 }，兼容模型包裹的说明文字 */
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

    private String toJson(List<JsonNode> nodes) {
        ArrayNode array = objectMapper.createArrayNode();
        nodes.forEach(array::add);
        return array.toString();
    }
}
