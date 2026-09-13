package com.example.lecture.agent.dto;

import com.example.lecture.dto.PendingActionResponse;
import lombok.Data;

import java.util.List;

@Data
public class AgentChatResponse {
    /** Agent 的最终回复文本 */
    private String reply;

    /** 本次对话实际调用的工具轨迹（前端展示"AI 做了什么"） */
    private List<AgentToolCallRecord> tools;

    /** 待确认写操作动作信息 */
    private PendingActionResponse action;
}
