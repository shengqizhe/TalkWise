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

    /** 评价分析分批参数（标定值：配置化，换模型/评测后可调整） */
    private Analysis analysis = new Analysis();

    @Data
    public static class Analysis {
        /** 每批内容预算（token 估算值）——一级参数，批大小的主约束 */
        private int batchMaxTokens = 3000;

        /** 每批条数安全阀（防大量短评把批切碎） */
        private int batchMaxItems = 40;

        /** 低于此长度的评价视为简短评价，不进入分析（仅计数） */
        private int minContentLength = 5;

        /** 单次 Reduce 输入预算（token 估算值），超过则触发两级 Reduce */
        private int reduceInputBudgetTokens = 8000;
    }
}
