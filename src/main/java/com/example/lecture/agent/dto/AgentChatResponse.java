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

    /** 本次对话查询到的讲座卡片（前端渲染为可点击列表） */
    private List<LectureCard> lectures;

    /** 待确认写操作动作信息 */
    private PendingActionResponse action;
}
