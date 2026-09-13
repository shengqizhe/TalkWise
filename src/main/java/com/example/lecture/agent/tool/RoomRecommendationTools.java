package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.service.RoomRecommendationService;
import com.example.lecture.entity.Lecture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 面向教师和管理员的教室推荐工具（只读规则，不修改任何数据）。 */
@Component
@RequiredArgsConstructor
public class RoomRecommendationTools {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RoomRecommendationService service;
    private final AgentRoleHelper roleHelper;

    @AgentTool(name = "recommendRoom", domain = "analysis", roles = {"admin", "teacher"},
            description = "按时间区间推荐可用教室：给定预计人数、开始时间与讲座时长，先排除该时段已被占用（未取消）的教室，"
                    + "再按容量贴合度排序，容量不足的教室也会列出并标注。仅做推荐，不修改讲座或教室数据。"
                    + "不适用：估算应设多少人请用 estimateCapacity；查询讲座信息请用 searchLectures。")
    public String recommendRoom(
            @AgentParam(name = "capacity", description = "预计听众人数（正整数）") Integer capacity,
            @AgentParam(name = "startTime", description = "讲座开始时间，格式 yyyy-MM-dd HH:mm:ss") String startTime,
            @AgentParam(name = "durationMinutes", description = "讲座时长（分钟），如 120") Integer durationMinutes,
            @AgentParam(name = "schoolName", description = "学校名称，可选；不传则在全部教室中推荐", required = false) String schoolName) {
        Long userId = AgentContext.getUserId();
        if (userId == null) return "请先登录后再推荐教室。";
        if (!roleHelper.isAdmin(userId) && !roleHelper.isTeacher(userId)) return "教室推荐功能面向教师与管理员开放。";
        if (capacity == null || capacity <= 0) return "预计人数必须为正整数。";
        if (startTime == null || startTime.isBlank()) return "请提供讲座开始时间，格式 yyyy-MM-dd HH:mm:ss。";
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(startTime.trim().replace(" ", "T"));
        } catch (Exception e) {
            return "开始时间格式不正确，请使用 yyyy-MM-dd HH:mm:ss。";
        }
        int duration = durationMinutes == null || durationMinutes <= 0
                ? Lecture.DEFAULT_DURATION_MINUTES : durationMinutes;

        RoomRecommendationService.RecommendResult result;
        try {
            result = service.recommend(capacity, start, duration, schoolName, RoomRecommendationService.DEFAULT_TOP_N);
        } catch (Exception e) {
            return "教室推荐失败：" + (e.getMessage() == null ? "数据查询异常" : e.getMessage());
        }
        if (result.recommendations().isEmpty()) {
            return "在 " + FMT.format(start) + "（时长 " + duration + " 分钟）没有可用教室。"
                    + (result.conflictExcludedCount() > 0
                    ? "其中 " + result.conflictExcludedCount() + " 间已排除：该时段存在未取消的讲座占用。" : "");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("推荐教室（").append(FMT.format(start)).append(" 起，时长 ").append(duration)
                .append(" 分钟，预计 ").append(capacity).append(" 人）：\n");
        int index = 1;
        for (RoomRecommendationService.Recommendation r : result.recommendations()) {
            sb.append(index++).append(". ").append(r.roomName())
                    .append("（ID：").append(r.locationId()).append("） ")
                    .append(r.reason()).append("\n");
        }
        if (result.conflictExcludedCount() > 0) {
            sb.append("已排除 ").append(result.conflictExcludedCount())
                    .append(" 间时间冲突教室（该时段存在未取消的讲座）。");
        }
        return sb.toString().trim();
    }
}
