package com.example.lecture.service;

import com.example.lecture.entity.User;

/**
 * 邮件服务接口
 */
public interface EmailService {
    
    /**
     * 发送简单文本邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    void sendSimpleEmail(String to, String subject, String content);
    
    /**
     * 发送HTML邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content HTML内容
     */
    void sendHtmlEmail(String to, String subject, String content);
    
    /**
     * 发送讲座报名成功通知邮件
     * @param user 用户信息
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendRegistrationSuccessEmail(User user, String lectureTitle, String startTime, String location);
    
    /**
     * 发送讲座即将开始提醒邮件
     * @param user 用户信息
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendUpcomingLectureEmail(User user, String lectureTitle, String startTime, String location);
    
    /**
     * 发送讲座取消通知邮件
     * @param user 用户信息
     * @param lectureTitle 讲座标题
     * @param reason 取消原因
     */
    void sendLectureCancelledEmail(User user, String lectureTitle, String reason);
    
    /**
     * 发送学生报名通知邮件给教师
     * @param teacher 教师信息
     * @param lectureTitle 讲座标题
     * @param studentName 学生姓名
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendStudentRegistrationNotificationEmail(User teacher, String lectureTitle, String studentName, String startTime, String location);
    
    /**
     * 发送取消报名通知邮件给学生
     * @param user 学生信息
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendRegistrationCancelEmail(User user, String lectureTitle, String startTime, String location);
    
    /**
     * 发送学生取消报名通知邮件给教师
     * @param teacher 教师信息
     * @param lectureTitle 讲座标题
     * @param studentName 学生姓名
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendStudentCancelNotificationEmail(User teacher, String lectureTitle, String studentName, String startTime, String location);
    
    /**
     * 发送讲座即将开始提醒邮件给讲师
     * @param teacher 讲师信息
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     * @param reminderMinutes 提醒时间（分钟）
     */
    void sendTeacherLectureReminderEmail(User teacher, String lectureTitle, String startTime, String location, int reminderMinutes);
}