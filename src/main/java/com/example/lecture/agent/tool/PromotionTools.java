package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentLlmClient;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Location;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.LocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 讲座宣传文案生成（单步 LLM 功能点，不写库）：替换原有的模板拼接方式。
 */
@Component
@RequiredArgsConstructor
public class PromotionTools {

    private final LectureMapper lectureMapper;
    private final LocationMapper locationMapper;
    private final AgentLlmClient llmClient;
    private final AgentRoleHelper roleHelper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    @AgentTool(
            name = "generatePromotion",
            description = "为指定讲座生成一段宣传文案（约 150-250 字，包含亮点、适合人群、时间地点，可直接用于海报或推送）。" +
                    "仅教师/管理员可用，教师只能为自己的讲座生成；lectureId 来自 searchLectures 结果中的 ID。"
    )
    public String generatePromotion(
            @AgentParam(name = "lectureId", description = "讲座 ID（数字，来自 searchLectures 或我的讲座列表）") Long lectureId
    ) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "请先登录后再生成宣传文案。";
        }
        Lecture lecture = lectureMapper.selectById(lectureId);
        if (lecture == null) {
            return "讲座不存在（ID=" + lectureId + "）。";
        }
        boolean admin = roleHelper.isAdmin(userId);
        if (!admin && !userId.equals(lecture.getOrganizerId())) {
            return "只能为自己的讲座生成宣传文案。";
        }

        String locationName = "未设置";
        if (lecture.getLocationId() != null) {
            Location loc = locationMapper.selectById(lecture.getLocationId());
            if (loc != null) {
                locationName = loc.getName();
            }
        }
        String prompt = "请为下面这场大学讲座写一段宣传文案，要求：\n"
                + "- 150-250 字，语言有吸引力但不夸张，适合学生群体转发；\n"
                + "- 包含：为什么值得听（结合内容提炼 2-3 个亮点）、适合人群、时间和地点；\n"
                + "- 直接输出文案正文，不要标题、不要解释、不要 Markdown 符号。\n\n"
                + "讲座信息：\n"
                + "标题：" + lecture.getTitle() + "\n"
                + "主讲人：" + lecture.getSpeaker() + "\n"
                + "时间：" + (lecture.getLectureTime() == null ? "待定" : FMT.format(lecture.getLectureTime())) + "\n"
                + "地点：" + locationName + "\n"
                + "简介：" + (lecture.getSummary() == null ? "（无）" : lecture.getSummary()) + "\n"
                + "内容：" + (lecture.getContent() == null ? "（无）" : lecture.getContent());
        try {
            String text = llmClient.generateText(prompt);
            if (text == null || text.isBlank()) {
                return "文案生成失败，请稍后重试。";
            }
            return "《" + lecture.getTitle() + "》宣传文案：\n\n" + text.trim();
        } catch (Exception e) {
            return "文案生成失败：" + e.getMessage() + "。";
        }
    }
}
