package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.dto.WebSocketMessage;
import com.example.lecture.service.WebSocketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * WebSocket控制器
 */
@Tag(name = "WebSocket", description = "WebSocket消息相关接口")
@RestController
@RequestMapping("/ws")
public class WebSocketController {

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Operation(summary = "发送消息")
    @MessageMapping("/send")
    @SendTo("/topic/messages")
    public WebSocketMessage handleMessage(WebSocketMessage message) {
        // 设置消息时间戳
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    @Operation(summary = "订阅消息")
    @MessageMapping("/user/queue/messages")
    public List<WebSocketMessage> subscribeMessages() {
        // 返回历史消息
        return null; // TODO: 实现历史消息查询
    }

    @Operation(summary = "发送讲座通知")
    @PostMapping("/lecture/notification")
    public Result<Void> sendLectureNotification(
            @RequestParam Long lectureId,
            @RequestParam String content) {
        webSocketService.sendLectureNotification(lectureId, content);
        return Result.success();
    }

    @Operation(summary = "发送报名通知")
    @PostMapping("/registration/notification")
    public Result<Void> sendRegistrationNotification(
            @RequestParam Long lectureId,
            @RequestParam Long userId) {
        webSocketService.sendRegistrationNotification(lectureId, userId);
        return Result.success();
    }

    /**
     * 处理讲座即将开始通知
     * 
     * @param message 包含讲座信息的消息
     */
    @MessageMapping("/notify/upcoming-lecture")
    public void handleUpcomingLectureNotification(WebSocketMessage message) {
        // 获取讲座信息
        Long lectureId = (Long) message.getData().get("lectureId");
        String lectureTitle = (String) message.getData().get("lectureTitle");
        String startTime = (String) message.getData().get("startTime");
        String location = (String) message.getData().get("location");
        
        // 发送通知给所有用户
        webSocketService.sendUpcomingLectureNotification(lectureId, lectureTitle, startTime, location);
    }
    
    @Operation(summary = "发送讲座即将开始通知")
    @PostMapping("/lecture/upcoming-notification")
    public Result<Void> sendUpcomingLectureNotification(
            @RequestParam Long lectureId,
            @RequestParam String lectureTitle,
            @RequestParam String startTime,
            @RequestParam String location) {
        webSocketService.sendUpcomingLectureNotification(lectureId, lectureTitle, startTime, location);
        return Result.success();
    }
    
    @Operation(summary = "发送讲座即将开始通知给特定用户")
    @PostMapping("/lecture/upcoming-notification/user")
    public Result<Void> sendUpcomingLectureNotificationToUser(
            @RequestParam String userId,
            @RequestParam Long lectureId,
            @RequestParam String lectureTitle,
            @RequestParam String startTime,
            @RequestParam String location) {
        webSocketService.sendUpcomingLectureNotificationToUser(userId, lectureId, lectureTitle, startTime, location);
        return Result.success();
    }
    
    /**
     * 处理报名成功消息
     */
    @MessageMapping("/registration/success")
    public void handleRegistrationSuccess(WebSocketMessage message) {
        // 获取报名信息
        Long userId = (Long) message.getData().get("userId");
        Long lectureId = (Long) message.getData().get("lectureId");
        String lectureTitle = (String) message.getData().get("lectureTitle");
        
        // 发送报名成功通知
        webSocketService.sendRegistrationSuccessNotification(userId, lectureId, lectureTitle);
    }
    
    /**
     * 处理学生报名通知给教师的消息
     */
    @MessageMapping("/registration/teacher-notification")
    public void handleStudentRegistrationToTeacher(WebSocketMessage message) {
        // 获取报名信息
        Long teacherId = (Long) message.getData().get("teacherId");
        Long lectureId = (Long) message.getData().get("lectureId");
        String lectureTitle = (String) message.getData().get("lectureTitle");
        String studentName = (String) message.getData().get("studentName");
        
        // 发送学生报名通知给教师
        webSocketService.sendStudentRegistrationNotificationToTeacher(teacherId, lectureId, lectureTitle, studentName);
    }
    
    @Operation(summary = "发送报名成功通知")
    @PostMapping("/registration/success-notification")
    public Result<Void> sendRegistrationSuccessNotification(
            @RequestParam Long userId,
            @RequestParam Long lectureId,
            @RequestParam String lectureTitle) {
        webSocketService.sendRegistrationSuccessNotification(userId, lectureId, lectureTitle);
        return Result.success();
    }
    
    @Operation(summary = "发送报名成功通知（包含邮件）")
    @PostMapping("/registration/success-notification-with-email")
    public Result<Void> sendRegistrationSuccessNotificationWithEmail(
            @RequestParam Long userId,
            @RequestParam Long lectureId,
            @RequestParam String lectureTitle,
            @RequestParam String startTime,
            @RequestParam String location) {
        webSocketService.sendRegistrationSuccessNotificationWithEmail(userId, lectureId, lectureTitle, startTime, location);
        return Result.success();
    }
    
    @Operation(summary = "发送讲座即将开始通知（包含邮件）")
    @PostMapping("/lecture/upcoming-notification-with-email")
    public Result<Void> sendUpcomingLectureNotificationWithEmail(
            @RequestParam Long lectureId,
            @RequestParam String lectureTitle,
            @RequestParam String startTime,
            @RequestParam String location) {
        webSocketService.sendUpcomingLectureNotificationWithEmail(lectureId, lectureTitle, startTime, location);
        return Result.success();
    }
    
    @Operation(summary = "发送学生报名通知给教师")
    @PostMapping("/registration/teacher-notification")
    public Result<Void> sendStudentRegistrationNotificationToTeacher(
            @RequestParam Long teacherId,
            @RequestParam Long lectureId,
            @RequestParam String lectureTitle,
            @RequestParam String studentName) {
        webSocketService.sendStudentRegistrationNotificationToTeacher(teacherId, lectureId, lectureTitle, studentName);
        return Result.success();
    }
}