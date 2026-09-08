package com.example.lecture.service.impl;

import com.example.lecture.entity.User;
import com.example.lecture.service.EmailService;
import com.example.lecture.config.LectureReminderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private TemplateEngine templateEngine;
    
    @Autowired
    private LectureReminderConfig lectureReminderConfig;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    @Override
    @Async("emailTaskExecutor")
    public void sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            
            mailSender.send(message);
            log.info("[异步]简单邮件发送成功，收件人：{}, 线程：{}", to, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]简单邮件发送失败，收件人：{}，错误信息：{}, 线程：{}", to, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendHtmlEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]HTML邮件发送成功，收件人：{}, 线程：{}", to, Thread.currentThread().getName());
        } catch (MessagingException e) {
            log.error("[异步]HTML邮件发送失败，收件人：{}，错误信息：{}, 线程：{}", to, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendRegistrationSuccessEmail(User user, String lectureTitle, String startTime, String location) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.warn("[异步]用户{}没有邮箱地址，无法发送报名成功邮件, 线程：{}", user.getRealName(), Thread.currentThread().getName());
            return;
        }
        
        try {
            Context context = new Context();
            context.setVariable("userName", user.getRealName());
            context.setVariable("lectureTitle", lectureTitle);
            context.setVariable("startTime", startTime);
            context.setVariable("location", location);
            
            String content = templateEngine.process("email/registration-success", context);
            String subject = "讲座报名成功通知 - " + lectureTitle;
            
            // 直接发送，不再调用sendHtmlEmail避免重复异步
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]报名成功邮件发送成功，收件人：{}, 讲座：{}, 线程：{}", user.getEmail(), lectureTitle, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]报名成功邮件发送失败，收件人：{}, 讲座：{}, 错误：{}, 线程：{}", user.getEmail(), lectureTitle, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendUpcomingLectureEmail(User user, String lectureTitle, String startTime, String location) {
        // 使用配置的默认提醒时间
        sendUpcomingLectureEmail(user, lectureTitle, startTime, location, lectureReminderConfig.getReminderMinutes());
    }
    
    /**
     * 发送即将开始的讲座提醒邮件（支持自定义提醒时间）
     * 
     * @param user 用户信息
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 地点
     * @param reminderMinutes 提醒时间（分钟）
     */
    @Async("emailTaskExecutor")
    public void sendUpcomingLectureEmail(User user, String lectureTitle, String startTime, String location, int reminderMinutes) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.warn("[异步]用户{}没有邮箱地址，无法发送讲座提醒邮件, 线程：{}", user.getRealName(), Thread.currentThread().getName());
            return;
        }
        
        try {
            Context context = new Context();
            context.setVariable("userName", user.getRealName());
            context.setVariable("lectureTitle", lectureTitle);
            context.setVariable("startTime", startTime);
            context.setVariable("location", location != null ? location : "待定");
            context.setVariable("reminderMinutes", reminderMinutes);
            
            String content = templateEngine.process("upcoming-lecture", context);
            String subject = String.format("🔔 讲座即将开始提醒 - %s (%d分钟后开始)", 
                    lectureTitle, reminderMinutes);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]成功发送即将开始提醒邮件给用户: {} - 讲座: {} - 时间: {} - 提醒时间: {}分钟, 线程：{}", 
                    user.getRealName(), lectureTitle, startTime, reminderMinutes, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]发送即将开始提醒邮件失败 - 用户: {}, 讲座: {}, 错误: {}, 线程：{}", 
                    user.getRealName(), lectureTitle, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendLectureCancelledEmail(User user, String lectureTitle, String reason) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.warn("[异步]用户{}没有邮箱地址，无法发送讲座取消邮件, 线程：{}", user.getRealName(), Thread.currentThread().getName());
            return;
        }
        
        try {
            Context context = new Context();
            context.setVariable("userName", user.getRealName());
            context.setVariable("lectureTitle", lectureTitle);
            context.setVariable("reason", reason);
            
            String content = templateEngine.process("email/lecture-cancelled", context);
            String subject = "讲座取消通知 - " + lectureTitle;
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]讲座取消邮件发送成功，收件人：{}, 讲座：{}, 线程：{}", user.getEmail(), lectureTitle, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]讲座取消邮件发送失败，收件人：{}, 讲座：{}, 错误：{}, 线程：{}", user.getEmail(), lectureTitle, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendStudentRegistrationNotificationEmail(User teacher, String lectureTitle, String studentName, String startTime, String location) {
        if (teacher.getEmail() == null || teacher.getEmail().trim().isEmpty()) {
            log.warn("[异步]教师{}没有邮箱地址，无法发送学生报名通知邮件, 线程：{}", teacher.getRealName(), Thread.currentThread().getName());
            return;
        }
        
        try {
            Context context = new Context();
            context.setVariable("teacherName", teacher.getRealName());
            context.setVariable("lectureTitle", lectureTitle);
            context.setVariable("studentName", studentName);
            context.setVariable("startTime", startTime);
            context.setVariable("location", location);
            
            String content = templateEngine.process("email/student-registration-notification", context);
            String subject = "学生报名通知 - " + lectureTitle;
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(teacher.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]学生报名通知邮件发送成功，收件人：{}, 学生：{}, 讲座：{}, 线程：{}", teacher.getEmail(), studentName, lectureTitle, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]学生报名通知邮件发送失败，收件人：{}, 学生：{}, 讲座：{}, 错误：{}, 线程：{}", teacher.getEmail(), studentName, lectureTitle, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendRegistrationCancelEmail(User user, String lectureTitle, String startTime, String location) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.warn("[异步]用户{}没有邮箱地址，无法发送取消报名邮件, 线程：{}", user.getRealName(), Thread.currentThread().getName());
            return;
        }
        
        try {
            Context context = new Context();
            context.setVariable("userName", user.getRealName());
            context.setVariable("lectureTitle", lectureTitle);
            context.setVariable("startTime", startTime);
            context.setVariable("location", location);
            
            String content = templateEngine.process("email/registration-cancel", context);
            String subject = "取消报名通知 - " + lectureTitle;
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]取消报名邮件发送成功，收件人：{}, 讲座：{}, 线程：{}", user.getEmail(), lectureTitle, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]取消报名邮件发送失败，收件人：{}, 讲座：{}, 错误：{}, 线程：{}", user.getEmail(), lectureTitle, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendStudentCancelNotificationEmail(User teacher, String lectureTitle, String studentName, String startTime, String location) {
        if (teacher.getEmail() == null || teacher.getEmail().trim().isEmpty()) {
            log.warn("[异步]教师{}没有邮箱地址，无法发送学生取消报名通知邮件, 线程：{}", teacher.getRealName(), Thread.currentThread().getName());
            return;
        }
        
        try {
            Context context = new Context();
            context.setVariable("teacherName", teacher.getRealName());
            context.setVariable("lectureTitle", lectureTitle);
            context.setVariable("studentName", studentName);
            context.setVariable("startTime", startTime);
            context.setVariable("location", location);
            
            String content = templateEngine.process("email/student-cancel-notification", context);
            String subject = "学生取消报名通知 - " + lectureTitle;
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(teacher.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]学生取消报名通知邮件发送成功，收件人：{}, 学生：{}, 讲座：{}, 线程：{}", teacher.getEmail(), studentName, lectureTitle, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]学生取消报名通知邮件发送失败，收件人：{}, 学生：{}, 讲座：{}, 错误：{}, 线程：{}", teacher.getEmail(), studentName, lectureTitle, e.getMessage(), Thread.currentThread().getName());
        }
    }
    
    @Override
    @Async("emailTaskExecutor")
    public void sendTeacherLectureReminderEmail(User teacher, String lectureTitle, String startTime, String location, int reminderMinutes) {
        if (teacher.getEmail() == null || teacher.getEmail().trim().isEmpty()) {
            log.warn("[异步]讲师{}没有邮箱地址，无法发送讲座提醒邮件, 线程：{}", teacher.getRealName(), Thread.currentThread().getName());
            return;
        }
        
        try {
            Context context = new Context();
            context.setVariable("teacherName", teacher.getRealName());
            context.setVariable("lectureTitle", lectureTitle);
            context.setVariable("startTime", startTime);
            context.setVariable("location", location != null ? location : "待定");
            context.setVariable("reminderMinutes", reminderMinutes);
            
            String content = templateEngine.process("teacher-lecture-reminder", context);
            String subject = String.format("🎯 讲座提醒通知 - %s (%d分钟后开始)", 
                    lectureTitle, reminderMinutes);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(teacher.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);
            log.info("[异步]成功发送讲座提醒邮件给讲师: {} - 讲座: {} - 时间: {} - 提醒时间: {}分钟, 线程：{}", 
                    teacher.getRealName(), lectureTitle, startTime, reminderMinutes, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("[异步]发送讲座提醒邮件失败 - 讲师: {}, 讲座: {}, 错误: {}, 线程：{}", 
                    teacher.getRealName(), lectureTitle, e.getMessage(), Thread.currentThread().getName());
        }
    }
}