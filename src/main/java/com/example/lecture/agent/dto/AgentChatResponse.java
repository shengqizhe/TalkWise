package com.example.lecture.agent.dto;

import lombok.Data;

@Data
public class AgentChatResponse {
    /** Agent 的最终回复文本 */
    private String reply;
}
