package com.example.lecture.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 配置（application.yml 的 agent.* 段）
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 通义千问 DashScope API Key（可用环境变量 AGENT_API_KEY 覆盖） */
    private String apiKey;

    /** OpenAI 兼容端点，如 https://dashscope.aliyuncs.com/compatible-mode/v1 */
    private String baseUrl;

    /** 模型名，如 qwen-turbo / qwen-plus */
    private String model;

    /** 单次对话最大工具轮数 */
    private int maxTurns = 8;

    /** 工具结果回填模型前的最大字符数（防上下文溢出） */
    private int maxToolResultChars = 2000;
}
