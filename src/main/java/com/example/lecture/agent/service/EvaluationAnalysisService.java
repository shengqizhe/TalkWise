package com.example.lecture.agent.service;

import com.example.lecture.agent.AgentLlmClient;
import com.example.lecture.agent.AgentProperties;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 评价分析服务（多批 Map-Reduce，可被同步工具与异步任务共用）：
 * 预处理（去重/短评过滤）→ 按 token 预算分批（bin packing）→ Map 提炼结构化产物 → Reduce 合并（支持两级）。
 * 参数来自 agent.analysis.* 配置（标定值）；通过 ProgressListener 支持进度上报（异步任务用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationAnalysisService {

    private static final int MAX_REDUCE_DEPTH = 3;

    private final AgentLlmClient llmClient;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 预处理结果 */
    public record PreparedData(List<Evaluation> items, int totalCount, int dupCount, int shortCount) {
    }

    /** 进度回调（每批完成后调用） */
    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    /** ① 预处理：内容去重合并 + 短评过滤（仅计数） */
    public PreparedData preprocess(List<Evaluation> all) {
        int minLength = Math.max(1, properties.getAnalysis().getMinContentLength());
        List<Evaluation> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int dup = 0;
        int shortCount = 0;
        for (Evaluation ev : all) {
            String content = ev.getContent() == null ? "" : ev.getContent().trim();
            if (content.length() < minLength) {
                shortCount++;
                continue;
            }
            if (!seen.add(content.replaceAll("\\s+", ""))) {
                dup++;
                continue;
            }
            items.add(ev);
        }
        return new PreparedData(items, all.size(), dup, shortCount);
    }

    /** ② 分批：token 预算为主约束，条数为安全阀（运行时按内容量动态装批） */
    public List<List<Evaluation>> packBatches(List<Evaluation> items) {
        int budget = properties.getAnalysis().getBatchMaxTokens();
        int maxItems = properties.getAnalysis().getBatchMaxItems();
        List<List<Evaluation>> batches = new ArrayList<>();
        List<Evaluation> current = new ArrayList<>();
        int currentTokens = 0;
        for (Evaluation ev : items) {
            int cost = estimateTokens(ev.getContent()) + 12; // 编号与格式开销
            boolean full = !current.isEmpty() && (current.size() >= maxItems || currentTokens + cost > budget);
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
     * ③④ 执行分析：逐批 Map（失败重试一次后跳过并计数）→ Reduce（超预算两级）→ 格式化报告。
     * @param listener 可为 null（同步调用不需要进度）
     */
    public String analyze(Lecture lecture, PreparedData prepared, List<List<Evaluation>> batches,
                          ProgressListener listener) {
        List<JsonNode> batchResults = new ArrayList<>();
        int failedBatches = 0;
        int done = 0;
        for (List<Evaluation> batch : batches) {
            JsonNode result = callMap(batch, lecture.getTitle());
            if (result == null) {
                failedBatches++;
            } else {
                batchResults.add(result);
            }
            done++;
            if (listener != null) {
                listener.onProgress(done, batches.size());
            }
        }
        if (batchResults.isEmpty()) {
            return "评价分析失败：全部批次未能得到有效结果，请稍后重试。";
        }
        JsonNode report = reduceTree(batchResults, lecture.getTitle(), 0);
        if (report == null) {
            return "评价分析失败：汇总阶段未能得到有效结果，请稍后重试。";
        }
        return formatReport(lecture.getTitle(), prepared, batches.size(), failedBatches, report);
    }

    // ==================== Map / Reduce ====================

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

    private JsonNode reduceTree(List<JsonNode> inputs, String title, int depth) {
        int budget = properties.getAnalysis().getReduceInputBudgetTokens();
        if (estimateTokens(toJson(inputs)) <= budget || depth >= MAX_REDUCE_DEPTH) {
            return callReduce(inputs, title);
        }
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

    private String formatReport(String title, PreparedData prepared, int batchCount,
                                int failedBatches, JsonNode report) {
        StringBuilder sb = new StringBuilder();
        sb.append("《").append(title).append("》评价分析报告\n");
        sb.append("· 数据口径：共 ").append(prepared.totalCount()).append(" 条评价，内容去重合并 ")
                .append(prepared.dupCount()).append(" 条，简短评价 ").append(prepared.shortCount())
                .append(" 条（仅计数未分析），实际分析 ").append(batchCount - failedBatches).append(" 批");
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

    /**
     * token 估算器：中文场景按 字符数×0.75 近似（略偏安全）。
     * 如需精确可替换为对应模型的 tokenizer——这是评测标定的接入点之一。
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() * 0.75);
    }

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
