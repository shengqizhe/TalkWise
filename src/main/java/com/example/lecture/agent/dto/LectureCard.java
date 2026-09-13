package com.example.lecture.agent.dto;

import lombok.Data;

/**
 * 讲座卡片：工具查询结果的结构化载体。
 *
 * <p>由工具直接产出并透传给前端渲染，不依赖模型复述——
 * 避免模型把讲座列表改写成 Markdown 表格、或把内部 ID 暴露给用户。
 */
@Data
public class LectureCard {
    /** 讲座ID（供前端点击跳转，不在回复文本中展示） */
    private Long id;
    private String title;
    private String speaker;
    /** 已格式化时间，如 2026-09-14 00:00 */
    private String lectureTime;
    private String locationName;
    /** 分类名，如 软件工程 */
    private String categoryName;
    /** 状态文案：即将开始 / 进行中 / 已结束 / 已取消 */
    private String statusText;
    private Integer capacity;
    private Integer registeredCount;
}
