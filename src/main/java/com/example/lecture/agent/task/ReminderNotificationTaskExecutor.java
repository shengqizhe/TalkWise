package com.example.lecture.agent.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.dto.WebSocketMessage;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Location;
import com.example.lecture.entity.Registration;
import com.example.lecture.entity.User;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.LocationMapper;
import com.example.lecture.mapper.RegistrationMapper;
import com.example.lecture.mapper.UserMapper;
import com.example.lecture.service.EmailService;
import com.example.lecture.service.WebSocketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 提醒通知任务执行器（type=reminder_notification）。
 *
 * <p>按讲座 ID 复用现有邮件服务与 WebSocket 推送：向该讲座所有有效报名学生
 * 发送即将开始提醒邮件与站内通知，并向任务发起人回执执行结果。
 *
 * <p>params 约定（JSON）：{@code {"lectureId":12}}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderNotificationTaskExecutor implements AgentTaskExecutor {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 有效报名状态（与统计口径一致：已取消不算） */
    private static final int REGISTRATION_ACTIVE = 1;

    private final LectureMapper lectureMapper;
    private final LocationMapper locationMapper;
    private final RegistrationMapper registrationMapper;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final WebSocketService webSocketService;
    private final AgentTaskExecutionSupport support;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getTaskType() {
        return AgentTask.TYPE_REMINDER_NOTIFICATION;
    }

    @Override
    public void execute(AgentTask task) {
        Long taskId = task.getId();
        if (!support.isActive(taskId)) {
            log.info("提醒任务 {} 已取消或不存在，跳过执行", taskId);
            return;
        }
        support.updateProgress(taskId, 10, "读取讲座与报名信息");

        JsonNode params = readParams(task);
        long lectureId = params.path("lectureId").asLong(0);
        if (lectureId <= 0) {
            throw new IllegalStateException("任务参数缺少 lectureId");
        }

        Lecture lecture = lectureMapper.selectById(lectureId);
        if (lecture == null) {
            throw new IllegalStateException("讲座不存在（ID=" + lectureId + "）");
        }
        String startTime = lecture.getLectureTime() == null
                ? "待定" : TIME_FMT.format(lecture.getLectureTime());
        String location = resolveLocationName(lecture.getLocationId());

        List<Registration> activeRegistrations = registrationMapper.selectList(
                        new LambdaQueryWrapper<Registration>()
                                .eq(Registration::getLectureId, lectureId)
                                .eq(Registration::getStatus, REGISTRATION_ACTIVE));
        if (activeRegistrations.isEmpty()) {
            String summary = "《" + lecture.getTitle() + "》暂无有效报名，无需发送提醒。";
            if (support.succeed(taskId, summary)) {
                support.notifyUser(task.getUserId(), taskId, summary);
            }
            return;
        }

        List<Long> userIds = activeRegistrations.stream()
                .map(Registration::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        int pushed = 0;
        int mailed = 0;
        int attempt = 0;
        for (Long userId : userIds) {
            if (!support.isActive(taskId)) {
                log.info("提醒任务 {} 在派发过程中被取消，停止剩余推送", taskId);
                return;
            }
            User user = users.get(userId);
            if (user == null) {
                continue;
            }
            if (pushReminder(userId, lecture, startTime, location)) {
                pushed++;
            }
            if (sendReminderEmail(user, lecture, startTime, location)) {
                mailed++;
            }
            attempt++;
            support.updateProgress(taskId, 10 + (int) (attempt * 85.0 / userIds.size()),
                    "已通知 " + attempt + "/" + userIds.size() + " 人");
        }

        String summary = "《" + lecture.getTitle() + "》提醒已派发：报名 " + userIds.size()
                + " 人，站内通知 " + pushed + " 人，邮件 " + mailed + " 人。";
        if (support.succeed(taskId, summary)) {
            support.notifyUser(task.getUserId(), taskId, summary);
        }
    }

    private JsonNode readParams(AgentTask task) {
        String raw = task.getParams();
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node == null || node.isNull() ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            throw new IllegalStateException("任务参数解析失败：" + e.getMessage(), e);
        }
    }

    private String resolveLocationName(Long locationId) {
        if (locationId == null) {
            return "待定";
        }
        Location location = locationMapper.selectById(locationId);
        return location == null || location.getName() == null ? "待定" : location.getName();
    }

    /** 站内推送：走现有 sendToUser 通道（前端铃铛接收） */
    private boolean pushReminder(Long userId, Lecture lecture, String startTime, String location) {
        try {
            WebSocketMessage message = new WebSocketMessage();
            message.setType("LECTURE_REMINDER");
            message.setReceiverId(userId);
            message.setContent("讲座即将开始：《" + lecture.getTitle() + "》，" + startTime + "，地点：" + location);
            message.getData().put("lectureId", lecture.getId());
            message.getData().put("lectureTitle", lecture.getTitle());
            message.getData().put("startTime", startTime);
            message.getData().put("location", location);
            webSocketService.sendToUser(message);
            return true;
        } catch (Exception e) {
            log.warn("讲座 {} 站内提醒推送失败（userId={}）：{}", lecture.getId(), userId, e.getMessage());
            return false;
        }
    }

    /** 邮件提醒：复用现有 EmailService（实现内部为异步执行并自行捕获异常） */
    private boolean sendReminderEmail(User user, Lecture lecture, String startTime, String location) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }
        try {
            emailService.sendUpcomingLectureEmail(user, lecture.getTitle(), startTime, location);
            return true;
        } catch (Exception e) {
            log.warn("讲座 {} 提醒邮件发送失败（userId={}）：{}", lecture.getId(), user.getId(), e.getMessage());
            return false;
        }
    }
}
