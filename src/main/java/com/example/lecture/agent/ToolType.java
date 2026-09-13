package com.example.lecture.agent;

/**
 * 工具类型：READ 直接执行；WRITE 直接执行并记审计日志（用户明确指令即授权，业务规则由 Service 层校验）。
 * 创建讲座类写操作不走本直接执行路径，而是由 prepareLectureCreation 生成草稿、用户确认后再提交。
 */
public enum ToolType {
    READ,
    WRITE
}
