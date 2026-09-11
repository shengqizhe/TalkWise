package com.example.lecture.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次工具调用的轨迹记录（供前端展示"AI 做了什么"）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentToolCallRecord {
    /** 工具名，如 searchLectures */
    private String name;
    /** 调用参数（JSON，超长已截断） */
    private String arguments;
}
