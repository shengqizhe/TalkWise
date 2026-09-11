package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.ToolType;
import com.example.lecture.dto.RegistrationLectureDTO;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报名相关工具：用户身份从 AgentContext 获取（当前登录用户），不接受模型传入的 userId，防止越权。
 * 写操作（报名/取消）在用户明确指令时由引擎直接执行，业务规则校验由 Service 层兜底，执行留审计日志。
 */
@Component
@RequiredArgsConstructor
public class RegistrationTools {

    private final RegistrationService registrationService;
    private final LectureMapper lectureMapper;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    @AgentTool(
            name = "getMyRegistrations",
            domain = "registration",
            description = "查询当前登录用户自己已报名的讲座（标题、讲座ID、主讲人、时间、地点）。"
                    + "适用：用户问\"我报了哪些\"\"我的安排\"。"
                    + "不适用：查某个讲座的所有报名学生（那是教师端名单功能，不在本对话范围）。"
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
                    .append("（ID：").append(reg.getLectureId()).append("）")
                    .append(" 主讲人：").append(reg.getSpeaker())
                    .append(" 时间：").append(reg.getStartTime() == null ? "待定" : FMT.format(reg.getStartTime()))
                    .append(" 地点：").append(reg.getLocation() == null ? "未设置" : reg.getLocation())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    @AgentTool(
            name = "registerLecture",
            domain = "registration",
            description = "为当前登录用户报名指定的讲座。仅在用户明确要求报名时调用；"
                    + "lectureId 必须来自 searchLectures 查询结果中的 ID，不要自行编造。"
                    + "不适用：用户只是表达兴趣（\"想参加\"）尚未明确报名时，先询问再调用。",
            type = ToolType.WRITE
    )
    public String registerLecture(
            @AgentParam(name = "lectureId", description = "要报名的讲座 ID（数字，来自 searchLectures 结果）") Long lectureId
    ) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "当前用户未登录，请先登录后再报名。";
        }
        Lecture lecture = lectureMapper.selectById(lectureId);
        String title = lecture == null ? ("ID=" + lectureId) : lecture.getTitle();
        try {
            registrationService.register(userId, lectureId);
            return "报名成功：《" + title + "》。";
        } catch (Exception e) {
            return "报名未成功：" + (e.getMessage() == null ? "未知原因" : e.getMessage());
        }
    }

    @AgentTool(
            name = "cancelRegistration",
            domain = "registration",
            description = "为当前登录用户取消某场讲座的报名。仅在用户明确要求取消时调用；"
                    + "lectureId 来自 searchLectures 或 getMyRegistrations 的结果。"
                    + "不适用：用户未报名该讲座或只是询问能否取消时，先查询报名记录核实。",
            type = ToolType.WRITE
    )
    public String cancelRegistration(
            @AgentParam(name = "lectureId", description = "要取消报名的讲座 ID（数字）") Long lectureId
    ) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "当前用户未登录，无法取消报名。";
        }
        Lecture lecture = lectureMapper.selectById(lectureId);
        String title = lecture == null ? ("ID=" + lectureId) : lecture.getTitle();
        try {
            registrationService.cancel(userId, lectureId);
            return "已取消报名：《" + title + "》。";
        } catch (Exception e) {
            return "取消报名未成功：" + (e.getMessage() == null ? "未知原因" : e.getMessage());
        }
    }
}
