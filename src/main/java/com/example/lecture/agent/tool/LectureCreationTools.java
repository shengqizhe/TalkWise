package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.dto.PendingActionResponse;
import com.example.lecture.entity.Lecture;
import com.example.lecture.service.PendingActionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LectureCreationTools {
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @AgentTool(name = "prepareLectureCreation", domain = "lecture", roles = {"admin", "teacher"},
            description = "根据用户明确提供的讲座信息生成待确认草稿，不会创建讲座。成功后返回 actionId；必须先让用户确认，确认请调用讲座确认接口。")
    public String prepareLectureCreation(
            @AgentParam(name = "title", description = "讲座标题") String title,
            @AgentParam(name = "summary", description = "讲座摘要") String summary,
            @AgentParam(name = "content", description = "讲座内容") String content,
            @AgentParam(name = "speaker", description = "主讲人") String speaker,
            @AgentParam(name = "categoryId", description = "讲座类别ID") Long categoryId,
            @AgentParam(name = "lectureTime", description = "讲座时间，格式 yyyy-MM-dd HH:mm:ss") String lectureTime,
            @AgentParam(name = "capacity", description = "讲座容量") Integer capacity,
            @AgentParam(name = "locationId", description = "地点ID，可选", required = false) Long locationId) {
        Long userId = AgentContext.getUserId();
        if (userId == null) return "当前用户未登录，无法创建讲座草稿。";
        try {
            Lecture draft = new Lecture();
            draft.setTitle(title); draft.setSummary(summary); draft.setContent(content); draft.setSpeaker(speaker);
            draft.setCategoryId(categoryId); draft.setLectureTime(LocalDateTime.parse(lectureTime.replace(" ", "T")));
            draft.setCapacity(capacity); draft.setLocationId(locationId);
            PendingActionResponse result = pendingActionService.prepareLectureCreation(userId, draft);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "讲座草稿生成失败：" + (e.getMessage() == null ? "参数或业务校验失败" : e.getMessage());
        }
    }
}
