package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.dto.RegistrationLectureDTO;
import com.example.lecture.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报名查询工具：用户身份从 AgentContext 获取（当前登录用户），不接受模型传入的 userId，防止越权。
 */
@Component
@RequiredArgsConstructor
public class RegistrationTools {

    private final RegistrationService registrationService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    @AgentTool(
            name = "getMyRegistrations",
            description = "查询当前用户自己已报名的讲座列表，返回讲座标题、主讲人、时间、地点与报名时间。"
    )
    public String getMyRegistrations() {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "当前用户未登录，无法查询报名记录。";
        }
        List<RegistrationLectureDTO> registrations;
        try {
            registrations = registrationService.getUserRegistrations(userId, 1);
        } catch (Exception e) {
            return "查询报名记录失败，请稍后再试。";
        }
        if (registrations == null || registrations.isEmpty()) {
            return "您目前还没有报名任何讲座。可以让我推荐一些讲座，或告诉我您的兴趣方向。";
        }
        StringBuilder sb = new StringBuilder("您已报名的讲座：\n");
        for (int i = 0; i < registrations.size(); i++) {
            RegistrationLectureDTO reg = registrations.get(i);
            sb.append(i + 1).append(". 《").append(reg.getLectureTitle()).append("》")
                    .append(" 主讲人：").append(reg.getSpeaker())
                    .append(" 时间：").append(reg.getStartTime() == null ? "待定" : FMT.format(reg.getStartTime()))
                    .append(" 地点：").append(reg.getLocation() == null ? "未设置" : reg.getLocation())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
