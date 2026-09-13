package com.example.lecture.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * LLM 客户端封装：对话循环（带工具）与单步文本生成（功能点工具用）。
 * 与 AgentEngine 解耦，避免工具类反向依赖引擎造成循环依赖。
 *
 * <p>兼容 OpenAI 协议的中转站：baseUrl 原样作为路径前缀，客户端只在其后拼
 * {@code /chat/completions}。若 baseUrl 没有路径段（如 {@code https://host}），
 * 会自动补 {@code /v1}，避免中转站 404；已带路径（{@code .../v1}、{@code .../compatible-mode/v1}）则保持不变。
 *
 * <p>所有模型故障统一抛 {@link LlmUnavailableException}，对用户只展示统一文案，真实原因进日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLlmClient {

    private final AgentProperties properties;

    private ChatLanguageModel model;

    @PostConstruct
    public void init() {
        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        this.model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(0.3)
                .timeout(timeout)
                .maxRetries(Math.max(0, properties.getMaxRetries()))
                .build();
        log.info("Agent 模型就绪: {} @ {}（超时 {}s，重试 {} 次）",
                properties.getModel(), baseUrl, timeout.toSeconds(), properties.getMaxRetries());
    }

    /**
     * 规范化 baseUrl：
     * <ul>
     *   <li>{@code https://host} → {@code https://host/v1}</li>
     *   <li>{@code https://host/} → {@code https://host/v1}</li>
     *   <li>{@code https://host/v1} 或 {@code .../compatible-mode/v1} → 原样保留</li>
     * </ul>
     * 判定依据：URL 去掉协议后若不含任何路径段，视为缺少版本前缀。
     */
    static String normalizeBaseUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String url = raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String withoutScheme = url.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        if (!withoutScheme.contains("/")) {
            return url + "/v1";
        }
        return url;
    }

    /** 对话调用（携带工具定义，供引擎使用） */
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpecification> tools) {
        try {
            return model.chat(ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(tools)
                    .build());
        } catch (Exception e) {
            throw wrap("对话调用模型失败", e);
        }
    }

    /** 单步文本生成（无工具，供文案生成/容量评估/评价分析等功能点使用） */
    public String generateText(String prompt) {
        try {
            ChatResponse response = model.chat(ChatRequest.builder()
                    .messages(List.of(UserMessage.from(prompt)))
                    .build());
            return response.aiMessage() == null ? null : response.aiMessage().text();
        } catch (Exception e) {
            throw wrap("文本生成调用模型失败", e);
        }
    }

    /** 统一包装为模型故障异常；真实原因记日志，不返回给终端用户 */
    private LlmUnavailableException wrap(String action, Exception e) {
        log.error("[Agent模型故障] {}：{}", action, rootMessage(e), e);
        return new LlmUnavailableException(action + "：" + rootMessage(e), e);
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }
}
