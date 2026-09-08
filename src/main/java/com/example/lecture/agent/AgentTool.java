package com.example.lecture.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 Agent 工具：注册表启动时扫描并生成 JSON Schema 交给模型。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {
    /** 工具名（模型调用时使用，建议小驼峰） */
    String name();

    /** 工具说明（模型判断何时调用，写清楚输入与返回） */
    String description();

    /** READ 直接执行；WRITE 需用户确认后执行 */
    ToolType type() default ToolType.READ;
}
