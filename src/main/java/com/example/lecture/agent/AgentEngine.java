package com.example.lecture.agent;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import com.example.lecture.agent.dto.AgentToolCallRecord;
import com.example.lecture.agent.dto.LectureCard;
import com.example.lecture.dto.PendingActionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agent 对话循环引擎（唯一入口）：
 * 用户消息 → 模型（携带工具定义）→ 工具调用请求 → 执行并回填 → 再问模型，直至模型给出最终文本。
 *
 * 决策权在模型：调哪个工具、传什么参数、何时收尾都由模型决定，代码只提供工具与循环。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEngine {

    private static final int MAX_HISTORY_MESSAGES = 20;

    private final AgentProperties properties;
    private final AgentToolRegistry registry;
    private final AgentLlmClient llmClient;
    private final ToolRouter toolRouter;
    private final AgentRoleHelper roleHelper;

    /** 内存会话：userId -> 历史消息（阶段后替换为 agent_message 表） */
    private final Map<Long, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    /** 每用户一把锁：同一用户的对话串行化，防止并发请求交错污染会话历史 */
    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    private static final SystemMessage SYSTEM = SystemMessage.from(
            "你是「知讲 TalkWise」的智能助手，服务于大学讲座系统。"
                    + "你可以调用工具完成：查询讲座、查看/办理报名与取消、查询统计数据（教师/管理员）、"
                    + "生成讲座宣传文案（教师）、分析讲座评价、估算讲座建议容量、推荐教室、"
                    + "以及创建/修改/取消/发布讲座（教师/管理员）等。"
                    + "规则：1) 只有工具能拿到真实数据，回答讲座/报名类问题必须先调用相关工具，不要编造；"
                    + "2) 工具返回空结果时如实告诉用户没有找到，并给出建议；"
                    + "3) 当用户明确要求执行操作（如报名、取消报名）时，直接调用对应工具执行，并以工具返回的真实结果如实告知，不要编造执行结果；"
                    + "4) 用户意图不明确（例如只说「想参加」但没说报名）时，先询问确认再执行；"
                    + "5) 容量估算只返回建议，不自动创建讲座或修改 lecture.capacity；LLM 只做定性判断，容量数字以工具计算结果为准。"
                    + "6) 创建/修改/取消/发布讲座都必须先调用对应的 prepare 工具生成草稿，"
                    + "把返回的 actionId 和完整讲座字段展示为确认卡，只有用户明确确认后才由确认接口执行；不得绕过草稿直接改库。"
                    + "7) 讲座列表由界面卡片直接展示给用户，你只需用一句话概述结果（例如「为你找到 3 场相关讲座」）；"
                    + "不要逐条罗列讲座、不要输出讲座 ID、不要使用 Markdown 表格或列对齐。"
                    + "讲座 ID 仅供你调用后续工具时使用，绝不能出现在给用户的回答里。"
                    + "8) 回复使用简洁自然的中文，少量要点可用短横线列出。");

    /** 一次对话的结果：最终回复 + 工具调用轨迹 + 查询到的讲座卡片 + 待确认动作 */
    public record ChatResult(String reply, List<AgentToolCallRecord> tools,
                             List<LectureCard> lectures, PendingActionResponse action) {
    }

    /**
     * 处理一条用户消息，返回最终回复与工具调用轨迹。
     * 同一用户的请求串行化（会话历史非线程安全），最多等待 30 秒，超时则提示稍后再发。
     */
    public ChatResult chat(Long userId, String userMessage) {
        Long key = userId == null ? -1L : userId;
        ReentrantLock lock = userLocks.computeIfAbsent(key, k -> new ReentrantLock());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!acquired) {
            return new ChatResult("我还在处理你的上一条消息，请稍等片刻再发送。", List.of(), List.of(), null);
        }
        try {
            return doChat(key, userMessage);
        } finally {
            lock.unlock();
        }
    }

    private ChatResult doChat(Long userId, String userMessage) {
        // 角色入上下文：供工具路由做身份过滤（学生看不到管理类工具）
        AgentContext.setRoles(roleHelper.rolesOf(userId));

        // 工具路由：本次对话的候选工具集经「身份 + 意图」过滤后固定，全程复用；
        // 模型只能调用候选集内的工具，候选集外的调用会被运行时拒绝（防止绕过过滤）。
        List<AgentToolRegistry.ToolDefinition> routedTools =
                toolRouter.route(registry.allTools(), userMessage, AgentContext.getRoles());
        List<ToolSpecification> toolSpecs = buildToolSpecs(routedTools);

        List<AgentToolCallRecord> usedTools = new ArrayList<>();
        // 本次请求重新开始收集讲座卡片（ThreadLocal 可能被同线程的上一个请求复用）
        AgentContext.clearLectureCards();
        List<ChatMessage> history = sessions.computeIfAbsent(userId,
                k -> new ArrayList<>());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SYSTEM);
        // 只带最近 N 条历史，避免上下文膨胀
        int from = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        messages.addAll(history.subList(from, history.size()));
        messages.add(UserMessage.from(userMessage));

        int maxTurns = Math.max(1, properties.getMaxTurns());
        try {
            for (int turn = 0; turn < maxTurns; turn++) {
                ChatResponse response = llmClient.chat(messages, toolSpecs);
                AiMessage ai = response.aiMessage();

                List<ToolExecutionRequest> requests = ai.toolExecutionRequests();
                if (requests == null || requests.isEmpty()) {
                    // 模型给出最终答复
                    String reply = ai.text() == null ? "（模型未返回内容）" : ai.text();
                    // 讲座列表已由卡片展示，这里清掉模型可能输出的表格与内部 ID
                    reply = sanitizeReply(reply, AgentContext.getLectureCards());
                    history.add(UserMessage.from(userMessage));
                    history.add(AiMessage.from(reply));
                    trimHistory(history);
                    return new ChatResult(reply, usedTools, AgentContext.getLectureCards(),
                            extractPendingAction(messages));
                }

                // 模型要调工具：把它的请求加入上下文，逐条执行后回填结果
                messages.add(ai);
                for (ToolExecutionRequest request : requests) {
                    // 同一工具同名同参只记录一次，避免前端出现重复标签
                    if (!containsToolCall(usedTools, request)) {
                        usedTools.add(new AgentToolCallRecord(request.name(), abbreviate(request.arguments())));
                    }
                    String result = dispatch(request, routedTools);
                    messages.add(ToolExecutionResultMessage.from(request, result));
                }
            }
        } catch (LlmUnavailableException e) {
            // 模型故障：给用户统一提示，真实原因已在客户端层记日志
            log.error("[Agent模型故障] 用户 {} 会话中断，已调用工具 {}", userId, usedTools.size());
            history.add(UserMessage.from(userMessage));
            history.add(AiMessage.from(LlmUnavailableException.USER_MESSAGE));
            trimHistory(history);
            return new ChatResult(LlmUnavailableException.USER_MESSAGE, usedTools, List.of(), null);
        }

        String fallback = "这个问题需要多步处理，我暂时没能完成，请换个问法或稍后再试。";
        history.add(UserMessage.from(userMessage));
        history.add(AiMessage.from(fallback));
        trimHistory(history);
        return new ChatResult(fallback, usedTools, AgentContext.getLectureCards(), null);
    }

    /**
     * 工具轨迹去重：同名工具只保留首次出现。
     *
     * <p>模型常换关键词反复调用同一工具（先"软件"再"软件工程"），
     * 按参数去重仍会出现多个相同标签，对用户没有信息量。</p>
     */
    private boolean containsToolCall(List<AgentToolCallRecord> used, ToolExecutionRequest request) {
        return used.stream().anyMatch(r -> r.getName().equals(request.name()));
    }

    /**
     * 清洗模型回复：讲座列表已由前端卡片呈现，表格与内部 ID 不应出现在对话里。
     *
     * <p>提示词约束并不可靠（模型仍可能输出表格、甚至编造不存在的 ID），
     * 因此这里由代码兜底：有卡片时剥离 Markdown 表格行与「ID：x」片段，
     * 清理后若只剩空内容，则替换为一句概述。
     */
    static String sanitizeReply(String reply, List<LectureCard> cards) {
        if (reply == null || reply.isBlank() || cards == null || cards.isEmpty()) {
            return reply;
        }
        StringBuilder kept = new StringBuilder();
        for (String line : reply.split("\n", -1)) {
            String trimmed = line.trim();
            // 丢弃 Markdown 表格：分隔行（|---|---|）与以 | 开头的数据行
            if (trimmed.startsWith("|") || trimmed.matches("^\\|?[\\s\\-:|]+\\|?$")) {
                continue;
            }
            // 去掉行内的「（ID：6）」「ID: 6」等内部标识
            String cleaned = trimmed
                    .replaceAll("[（(]\\s*ID\\s*[:：]?\\s*\\d+\\s*[）)]", "")
                    .replaceAll("(?i)\\bID\\s*[:：]\\s*\\d+", "")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            if (!cleaned.isEmpty()) {
                kept.append(cleaned).append("\n");
            }
        }
        String result = kept.toString().trim();
        if (result.isEmpty()) {
            return cards.size() == 1
                    ? "为你找到 1 场相关讲座，详见下方卡片。"
                    : "为你找到 " + cards.size() + " 场相关讲座，详见下方卡片。";
        }
        return result;
    }

    private PendingActionResponse extractPendingAction(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage result && result.text() != null) {
                try {
                    if (result.text().contains("\"actionId\"") && result.text().contains("\"status\":\"PENDING\"")) {
                        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(result.text(), PendingActionResponse.class);
                    }
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 120 ? value.substring(0, 120) + "…" : value;
    }

    /** 分发工具调用：先校验在本次候选集内（拒绝候选集外调用），再执行；WRITE 记审计日志 */
    private String dispatch(ToolExecutionRequest request,
                            List<AgentToolRegistry.ToolDefinition> routedTools) {
        boolean inCandidate = routedTools.stream().anyMatch(d -> d.name.equals(request.name()));
        if (!inCandidate) {
            log.warn("[Agent审计] 拒绝候选集外工具调用: {}", request.name());
            return "工具不可用：" + request.name() + "（不在当前可用范围内，请使用已提供的工具完成请求）。";
        }
        AgentToolRegistry.ToolDefinition def = registry.find(request.name());
        if (def == null) {
            return "工具不存在：" + request.name();
        }
        if (def.type == ToolType.WRITE) {
            log.info("[Agent审计] 用户 {} 执行写操作 {}，参数 {}",
                    AgentContext.getUserId(), request.name(), request.arguments());
        }
        String result = registry.execute(def, request.arguments());
        int maxChars = properties.getMaxToolResultChars();
        if (result.length() > maxChars) {
            result = result.substring(0, maxChars) + "…（结果过长已截断）";
        }
        return result;
    }

    /** 把候选工具定义转成模型可读的 JSON Schema */
    private List<ToolSpecification> buildToolSpecs(List<AgentToolRegistry.ToolDefinition> routed) {
        List<ToolSpecification> specs = new ArrayList<>();
        for (AgentToolRegistry.ToolDefinition def : routed) {
            Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (AgentToolRegistry.ToolDefinition.ParamMeta p : def.params) {
                Map<String, Object> prop = new HashMap<>();
                prop.put("type", "string");
                prop.put("description", p.description);
                properties.put(p.name, prop);
                if (p.required) {
                    required.add(p.name);
                }
            }
            ToolParameters parameters = ToolParameters.builder()
                    .properties(properties)
                    .required(required)
                    .build();
            specs.add(ToolSpecification.builder()
                    .name(def.name)
                    .description(def.description)
                    .parameters(parameters)
                    .build());
        }
        return specs;
    }

    private void trimHistory(List<ChatMessage> history) {
        while (history.size() > MAX_HISTORY_MESSAGES * 2) {
            history.remove(0);
        }
    }
}
