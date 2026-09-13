package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.ToolType;
import com.example.lecture.dto.PendingActionResponse;
import com.example.lecture.entity.Lecture;
import com.example.lecture.service.PendingActionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 讲座写动作工具：创建 / 修改 / 取消 / 发布。
 * 全部为 WRITE 类型，只生成待确认草稿，不直接写库；用户明确确认后由确认接口执行。
 */
@Component
@RequiredArgsConstructor
public class LectureCreationTools {
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @AgentTool(name = "prepareLectureCreation", domain = "lecture", roles = {"admin", "teacher"},
            type = ToolType.WRITE,
            description = "根据用户明确提供的讲座信息生成待确认草稿，不会创建讲座。"
                    + "适用：教师/管理员要新建一场讲座且已提供标题、摘要、内容、主讲人、类别、时间、容量。"
                    + "不适用：查询讲座请用 searchLectures；修改/取消/发布已有讲座请用对应 prepare 工具。"
                    + "成功后返回 actionId，必须让用户明确确认后再调用确认接口。")
    public String prepareLectureCreation(
            @AgentParam(name = "title", description = "讲座标题") String title,
            @AgentParam(name = "summary", description = "讲座摘要") String summary,
            @AgentParam(name = "content", description = "讲座内容") String content,
            @AgentParam(name = "speaker", description = "主讲人") String speaker,
            @AgentParam(name = "categoryId", description = "讲座类别ID") Long categoryId,
            @AgentParam(name = "lectureTime", description = "讲座时间，格式 yyyy-MM-dd HH:mm:ss") String lectureTime,
            @AgentParam(name = "capacity", description = "讲座容量") Integer capacity,
            @AgentParam(name = "durationMinutes", description = "讲座时长（分钟），不填默认 120", required = false) Integer durationMinutes,
            @AgentParam(name = "locationId", description = "地点ID，可选") Long locationId) {
        Long userId = AgentContext.getUserId();
        if (userId == null) return "当前用户未登录，无法创建讲座草稿。";
        try {
            Lecture draft = new Lecture();
            draft.setTitle(title);
            draft.setSummary(summary);
            draft.setContent(content);
            draft.setSpeaker(speaker);
            draft.setCategoryId(categoryId);
            draft.setLectureTime(parseTime(lectureTime));
            draft.setCapacity(capacity);
            draft.setDurationMinutes(durationMinutes);
            draft.setLocationId(locationId);
            return toJson(pendingActionService.prepareLectureCreation(userId, draft));
        } catch (Exception e) {
            return "讲座草稿生成失败：" + message(e);
        }
    }

    @AgentTool(name = "prepareLectureUpdate", domain = "lecture", roles = {"admin", "teacher"},
            type = ToolType.WRITE,
            description = "为已有讲座生成待确认修改草稿，不会直接修改讲座。"
                    + "适用：教师/管理员要改讲座的标题、摘要、内容、主讲人、类别、地点、时间、时长或容量。"
                    + "参数来源：lectureId 必须来自 searchLectures 或我的讲座列表；只传用户明确要改的字段。"
                    + "不适用：新建讲座请用 prepareLectureCreation。")
    public String prepareLectureUpdate(
            @AgentParam(name = "lectureId", description = "讲座 ID（数字）") Long lectureId,
            @AgentParam(name = "title", description = "新标题，可选", required = false) String title,
            @AgentParam(name = "summary", description = "新摘要，可选", required = false) String summary,
            @AgentParam(name = "content", description = "新内容，可选", required = false) String content,
            @AgentParam(name = "speaker", description = "新主讲人，可选", required = false) String speaker,
            @AgentParam(name = "categoryId", description = "新类别ID，可选", required = false) Long categoryId,
            @AgentParam(name = "lectureTime", description = "新讲座时间 yyyy-MM-dd HH:mm:ss，可选", required = false) String lectureTime,
            @AgentParam(name = "durationMinutes", description = "新讲座时长（分钟），可选", required = false) Integer durationMinutes,
            @AgentParam(name = "capacity", description = "新容量，可选", required = false) Integer capacity,
            @AgentParam(name = "locationId", description = "新地点ID，可选", required = false) Long locationId) {
        Long userId = AgentContext.getUserId();
        if (userId == null) return "当前用户未登录，无法修改讲座。";
        try {
            Lecture patch = new Lecture();
            patch.setTitle(title);
            patch.setSummary(summary);
            patch.setContent(content);
            patch.setSpeaker(speaker);
            patch.setCategoryId(categoryId);
            patch.setLectureTime(parseTime(lectureTime));
            patch.setDurationMinutes(durationMinutes);
            patch.setCapacity(capacity);
            patch.setLocationId(locationId);
            return toJson(pendingActionService.prepareLectureUpdate(userId, lectureId, patch));
        } catch (Exception e) {
            return "修改草稿生成失败：" + message(e);
        }
    }

    @AgentTool(name = "prepareLectureCancel", domain = "lecture", roles = {"admin", "teacher"},
            type = ToolType.WRITE,
            description = "为已有讲座生成待确认取消草稿，不会直接取消讲座。"
                    + "适用：教师/管理员要取消自己的一场讲座。"
                    + "参数来源：lectureId 必须来自 searchLectures 或我的讲座列表；reason 为取消原因，可选。")
    public String prepareLectureCancel(
            @AgentParam(name = "lectureId", description = "讲座 ID（数字）") Long lectureId,
            @AgentParam(name = "reason", description = "取消原因，可选", required = false) String reason) {
        Long userId = AgentContext.getUserId();
        if (userId == null) return "当前用户未登录，无法取消讲座。";
        try {
            return toJson(pendingActionService.prepareLectureCancel(userId, lectureId, reason));
        } catch (Exception e) {
            return "取消草稿生成失败：" + message(e);
        }
    }

    @AgentTool(name = "prepareLecturePublish", domain = "lecture", roles = {"admin", "teacher"},
            type = ToolType.WRITE,
            description = "为已有讲座生成待确认的发布/下架草稿，不会直接变更发布状态。"
                    + "适用：教师/管理员要把讲座发布给学生（publishStatus=1）或下架（publishStatus=0）。"
                    + "参数来源：lectureId 必须来自 searchLectures 或我的讲座列表。")
    public String prepareLecturePublish(
            @AgentParam(name = "lectureId", description = "讲座 ID（数字）") Long lectureId,
            @AgentParam(name = "publishStatus", description = "发布状态：1=发布，0=下架") Integer publishStatus) {
        Long userId = AgentContext.getUserId();
        if (userId == null) return "当前用户未登录，无法变更发布状态。";
        try {
            return toJson(pendingActionService.prepareLecturePublish(userId, lectureId, publishStatus));
        } catch (Exception e) {
            return "发布草稿生成失败：" + message(e);
        }
    }

    /** 解析 yyyy-MM-dd HH:mm:ss；为空时返回 null（表示不修改该字段） */
    private LocalDateTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return LocalDateTime.parse(text.trim().replace(" ", "T"));
    }

    private String toJson(PendingActionResponse response) throws Exception {
        return objectMapper.writeValueAsString(response);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? "参数或业务校验失败" : e.getMessage();
    }
}
