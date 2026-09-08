package com.example.lecture.agent;

/**
 * 工具类型：READ 直接执行；WRITE 走确认审批流（阶段 B 接入）
 */
public enum ToolType {
    READ,
    WRITE
}
