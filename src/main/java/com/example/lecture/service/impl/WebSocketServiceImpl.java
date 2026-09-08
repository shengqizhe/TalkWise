package com.example.lecture.service.impl;

import com.example.lecture.dto.WebSocketMessage;
import com.example.lecture.entity.User;
import com.example.lecture.service.EmailService;
import com.example.lecture.service.RegistrationService;
import com.example.lecture.service.UserService;
import com.example.lecture.service.WebSocketService;
import com.example.lecture.dto.RegistrationUserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.dev33.satoken.SaManager.log;

/**
 * WebSocket消息服务实现类
 * 实现了系统通知功能
 */
@Service
public class WebSocketServiceImpl implements WebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private UserService userService;
    
    @Lazy
    @Autowired
    private RegistrationService registrationService;

    /**
     * 发送消息给指定用户
     */
    @Override
    public void sendToUser(WebSocketMessage message) {
        // 设置消息时间
        message.setTimestamp(Instant.now().toEpochMilli());
        // 设置消息状态为未读
        message.setStatus(0);
        
        // 发送消息
        messagingTemplate.convertAndSendToUser(
                message.getReceiverId().toString(),
                "/queue/messages",
                message
        );
    }

    /**
     * 发送消息给所有用户
     */
    @Override
    public void sendToAll(WebSocketMessage message) {
        // 设置消息时间
        message.setTimestamp(Instant.now().toEpochMilli());
        // 设置消息状态为未读
        message.setStatus(0);
        
        // 发送消息
        messagingTemplate.convertAndSend("/topic/messages", message);
    }

    /**
     * 发送讲座通知
     */
    @Override
    public void sendLectureNotification(Long lectureId, String content) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("LECTURE_NOTIFICATION");
        message.setContent(content);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        
        // 设置消息时间
        message.setTimestamp(Instant.now().toEpochMilli());
        // 设置消息状态为未读
        message.setStatus(0);
        
        // 发送到讲座通知队列
        messagingTemplate.convertAndSend("/topic/lecture-notification", message);
    }

    /**
     * 发送报名通知
     */
    @Override
    public void sendRegistrationNotification(Long lectureId, Long userId) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("REGISTRATION_NOTIFICATION");
        message.setReceiverId(userId);
        message.setContent("您已成功报名讲座，讲座ID：" + lectureId);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        message.getData().put("userId", userId);
        
        // 发送给指定用户
        sendToUser(message);
    }
    
    /**
     * 发送讲座即将开始通知给所有用户
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    @Override
    public void sendUpcomingLectureNotification(Long lectureId, String lectureTitle, String startTime, String location) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("upcoming_lecture");
        message.setContent("讲座即将开始：" + lectureTitle);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        message.getData().put("lectureTitle", lectureTitle);
        message.getData().put("startTime", startTime);
        message.getData().put("location", location);
        
        // 广播通知所有在线用户
        messagingTemplate.convertAndSend("/topic/notifications", message);
    }
    
    /**
     * 发送讲座即将开始通知给特定用户
     * @param userId 用户ID
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    @Override
    public void sendUpcomingLectureNotificationToUser(String userId, Long lectureId, String lectureTitle, String startTime, String location) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("upcoming_lecture");
        message.setContent("讲座即将开始：" + lectureTitle);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        message.getData().put("lectureTitle", lectureTitle);
        message.getData().put("startTime", startTime);
        message.getData().put("location", location);
        
        // 发送给特定用户
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", message);
    }
    
    /**
     * 发送报名成功通知给学生
     */
    @Override
    public void sendRegistrationSuccessNotification(Long userId, Long lectureId, String lectureTitle) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("REGISTRATION_SUCCESS");
        message.setReceiverId(userId);
        message.setContent(lectureTitle);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        message.getData().put("lectureTitle", lectureTitle);
        message.getData().put("notificationType", "registration_success");
        
        // 设置消息时间
        message.setTimestamp(Instant.now().toEpochMilli());
        // 设置消息状态为未读
        message.setStatus(0);
        
        // 发送到报名成功通知队列
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/registration-success",
                message
        );
    }
    
    /**
     * 发送学生报名通知给教师
     */
    @Override
    public void sendStudentRegistrationNotificationToTeacher(Long teacherId, Long lectureId, String lectureTitle, String studentName) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("STUDENT_REGISTRATION");
        message.setReceiverId(teacherId);
        message.setContent("学生 " + studentName + " 报名了您的讲座：" + lectureTitle);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        message.getData().put("lectureTitle", lectureTitle);
        message.getData().put("studentName", studentName);
        message.getData().put("notificationType", "student_registration");
        
        // 设置消息时间
        message.setTimestamp(Instant.now().toEpochMilli());
        // 设置消息状态为未读
        message.setStatus(0);
        
        // 发送到学生报名通知队列
        messagingTemplate.convertAndSendToUser(
                teacherId.toString(),
                "/queue/student-registration",
                message
        );
    }
    
    @Override
    public void sendRegistrationSuccessNotificationWithEmail(Long userId, Long lectureId, String lectureTitle, String startTime, String location) {
        // 发送WebSocket通知
        sendRegistrationSuccessNotification(userId, lectureId, lectureTitle);
        
        // 发送邮件通知
        User user = userService.getById(userId);
        if (user != null) {
            emailService.sendRegistrationSuccessEmail(user, lectureTitle, startTime, location);
        }
    }
    
    @Override
    public void sendUpcomingLectureNotificationWithEmail(Long lectureId, String lectureTitle, String startTime, String location) {
        log.info("🚀 开始发送即将开始通知 - 讲座: {}, 开始时间: {}, 地点: {}", lectureTitle, startTime, location);
        
        // 发送WebSocket通知
        try {
            sendUpcomingLectureNotification(lectureId, lectureTitle, startTime, location);
            log.info("📡 WebSocket通知发送成功 - 讲座: {}", lectureTitle);
        } catch (Exception e) {
            log.error("📡 WebSocket通知发送失败 - 讲座: {}, 错误: {}", lectureTitle, e.getMessage());
        }
        
        // 获取所有报名用户并发送邮件
        try {
            List<RegistrationUserDTO> registrations = registrationService.getLectureRegistrations(lectureId);
            
            if (registrations == null || registrations.isEmpty()) {
                log.info("📧 该讲座暂无报名用户，跳过邮件发送 - 讲座: {}", lectureTitle);
                return;
            }
            
            // 统计信息
            int totalRegistrations = registrations.size();
            int activeRegistrations = (int) registrations.stream().filter(r -> r.getStatus() == 1).count();
            int emailsSent = 0;
            int emailsFailed = 0;
            
            log.info("📧 开始发送邮件提醒 - 讲座: {}, 总报名数: {}, 有效报名数: {}", 
                    lectureTitle, totalRegistrations, activeRegistrations);
            
            for (RegistrationUserDTO registration : registrations) {
                // 只给已报名状态的用户发送邮件
                if (registration.getStatus() == 1) {
                    User user = userService.getById(registration.getUserId());
                    if (user != null) {
                        try {
                            emailService.sendUpcomingLectureEmail(user, lectureTitle, startTime, location);
                            emailsSent++;
                            log.debug("✅ 邮件发送成功 - 用户: {} ({})", user.getRealName(), user.getEmail());
                        } catch (Exception emailException) {
                            emailsFailed++;
                            log.warn("❌ 邮件发送失败 - 用户: {} ({}), 错误: {}", 
                                user.getRealName(), user.getEmail(), emailException.getMessage());
                        }
                    } else {
                        emailsFailed++;
                        log.warn("❌ 用户信息获取失败 - 用户ID: {}", registration.getUserId());
                    }
                } else {
                    log.debug("⏭️ 跳过非活跃报名 - 用户ID: {}, 状态: {}", 
                            registration.getUserId(), registration.getStatus());
                }
            }
            
            log.info("🎯 即将开始通知发送完成 - 讲座: {}, 邮件发送成功: {}, 邮件发送失败: {}, 总处理数: {}", 
                    lectureTitle, emailsSent, emailsFailed, activeRegistrations);
                
        } catch (Exception e) {
            log.error("💥 获取讲座报名用户列表失败 - 讲座ID: {}, 讲座: {}, 错误: {}", 
                    lectureId, lectureTitle, e.getMessage(), e);
        }
    }
    
    /**
     * 发送取消报名通知给学生
     */
    @Override
    public void sendRegistrationCancelNotification(Long userId, Long lectureId, String lectureTitle) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("REGISTRATION_CANCEL");
        message.setReceiverId(userId);
        message.setContent("您已成功取消报名讲座：" + lectureTitle);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        message.getData().put("lectureTitle", lectureTitle);
        message.getData().put("notificationType", "registration_cancel");
        
        // 设置消息时间
        message.setTimestamp(Instant.now().toEpochMilli());
        // 设置消息状态为未读
        message.setStatus(0);
        
        // 发送到取消报名通知队列
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/registration-cancel",
                message
        );
    }
    
    /**
     * 发送学生取消报名通知给教师
     */
    @Override
    public void sendStudentCancelNotificationToTeacher(Long teacherId, Long lectureId, String lectureTitle, String studentName) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("STUDENT_CANCEL");
        message.setReceiverId(teacherId);
        message.setContent("学生 " + studentName + " 取消了您的讲座报名：" + lectureTitle);
        
        if (message.getData() == null) {
            message.setData(new HashMap<>());
        }
        message.getData().put("lectureId", lectureId);
        message.getData().put("lectureTitle", lectureTitle);
        message.getData().put("studentName", studentName);
        message.getData().put("notificationType", "student_cancel");
        
        // 设置消息时间
        message.setTimestamp(Instant.now().toEpochMilli());
        // 设置消息状态为未读
        message.setStatus(0);
        
        // 发送到学生取消报名通知队列
        messagingTemplate.convertAndSendToUser(
                teacherId.toString(),
                "/queue/student-cancel",
                message
        );
    }
}