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

import java.util.List;

/**
 * LLM 客户端封装：对话循环（带工具）与单步文本生成（功能点工具用）。
 * 与 AgentEngine 解耦，避免工具类反向依赖引擎造成循环依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLlmClient {

    private final AgentProperties properties;

    private ChatLanguageModel model;

    @PostConstruct
    public void init() {
        this.model = OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(0.3)
                .build();
        log.info("Agent 模型就绪: {} @ {}", properties.getModel(), properties.getBaseUrl());
    }

    /** 对话调用（携带工具定义，供引擎使用） */
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return model.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(tools)
                .build());
    }

    /** 单步文本生成（无工具，供文案生成/情感分析等功能点使用） */
    public String generateText(String prompt) {
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .build());
        return response.aiMessage() == null ? null : response.aiMessage().text();
    }
}
