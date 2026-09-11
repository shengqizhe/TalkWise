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
                    + "生成讲座宣传文案（教师）、分析讲座评价情感（教师）等。"
                    + "规则：1) 只有工具能拿到真实数据，回答讲座/报名类问题必须先调用相关工具，不要编造；"
                    + "2) 工具返回空结果时如实告诉用户没有找到，并给出建议；"
                    + "3) 当用户明确要求执行操作（如报名、取消报名）时，直接调用对应工具执行，并以工具返回的真实结果如实告知，不要编造执行结果；"
                    + "4) 用户意图不明确（例如只说「想参加」但没说报名）时，先询问确认再执行；"
                    + "5) 回复使用简洁自然的中文，讲座信息用要点列出。");

    /** 一次对话的结果：最终回复 + 本次实际调用的工具轨迹（前端展示"AI 做了什么"） */
    public record ChatResult(String reply, List<AgentToolCallRecord> tools) {
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
            return new ChatResult("我还在处理你的上一条消息，请稍等片刻再发送。", List.of());
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
        List<ChatMessage> history = sessions.computeIfAbsent(userId,
                k -> new ArrayList<>());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SYSTEM);
        // 只带最近 N 条历史，避免上下文膨胀
        int from = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        messages.addAll(history.subList(from, history.size()));
        messages.add(UserMessage.from(userMessage));

        int maxTurns = Math.max(1, properties.getMaxTurns());
        for (int turn = 0; turn < maxTurns; turn++) {
            ChatResponse response = llmClient.chat(messages, toolSpecs);
            AiMessage ai = response.aiMessage();

            List<ToolExecutionRequest> requests = ai.toolExecutionRequests();
            if (requests == null || requests.isEmpty()) {
                // 模型给出最终答复
                String reply = ai.text() == null ? "（模型未返回内容）" : ai.text();
                history.add(UserMessage.from(userMessage));
                history.add(AiMessage.from(reply));
                trimHistory(history);
                return new ChatResult(reply, usedTools);
            }

            // 模型要调工具：把它的请求加入上下文，逐条执行后回填结果
            messages.add(ai);
            for (ToolExecutionRequest request : requests) {
                usedTools.add(new AgentToolCallRecord(request.name(), abbreviate(request.arguments())));
                String result = dispatch(request, routedTools);
                messages.add(ToolExecutionResultMessage.from(request, result));
            }
        }

        String fallback = "这个问题需要多步处理，我暂时没能完成，请换个问法或稍后再试。";
        history.add(UserMessage.from(userMessage));
        history.add(AiMessage.from(fallback));
        trimHistory(history);
        return new ChatResult(fallback, usedTools);
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
