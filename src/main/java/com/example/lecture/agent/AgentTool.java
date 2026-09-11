package com.example.lecture.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 Agent 工具：注册表启动时扫描并生成 JSON Schema 交给模型。
 *
 * 描述（description）书写规范：
 *   1) 做什么：一句话说明工具能力；
 *   2) 什么时候用：明确触发场景；
 *   3) 什么时候不用：给出排他说明（避免与相近工具混淆）；
 *   4) 参数来源约束：如 "lectureId 必须来自 searchLectures 结果"。
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

    /**
     * 工具域标签（用于调用前的意图过滤/路由）：
     * lecture=讲座查询 / registration=报名 / statistics=统计 / content=内容生成 / analysis=分析 / general=通用
     */
    String domain() default "general";

    /**
     * 允许使用的角色（小写：admin / teacher / student）。
     * 空数组 = 所有角色（含未登录游客）可用；非空则要求当前用户具备其中任一角色。
     */
    String[] roles() default {};
}
