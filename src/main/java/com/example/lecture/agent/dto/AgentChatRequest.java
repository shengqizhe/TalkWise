package com.example.lecture.agent.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    /** 用户输入的消息 */
    private String message;
}
