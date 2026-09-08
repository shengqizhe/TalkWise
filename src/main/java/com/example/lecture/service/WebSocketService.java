package com.example.lecture.service;

import com.example.lecture.dto.WebSocketMessage;

/**
 * WebSocket服务接口
 */
public interface WebSocketService {
    /**
     * 发送消息给指定用户
     */
    void sendToUser(WebSocketMessage message);
    
    /**
     * 发送消息给所有用户
     */
    void sendToAll(WebSocketMessage message);
    
    /**
     * 发送讲座通知
     */
    void sendLectureNotification(Long lectureId, String content);
    
    /**
     * 发送报名通知
     */
    void sendRegistrationNotification(Long lectureId, Long userId);
    
    /**
     * 发送讲座即将开始通知给所有用户
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendUpcomingLectureNotification(Long lectureId, String lectureTitle, String startTime, String location);
    
    /**
     * 发送讲座即将开始通知给特定用户
     * @param userId 用户ID
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendUpcomingLectureNotificationToUser(String userId, Long lectureId, String lectureTitle, String startTime, String location);
    
    /**
     * 发送报名成功通知给学生
     * @param userId 学生用户ID
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     */
    void sendRegistrationSuccessNotification(Long userId, Long lectureId, String lectureTitle);
    
    /**
     * 发送学生报名通知给教师
     * @param teacherId 教师用户ID
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param studentName 学生姓名
     */
    void sendStudentRegistrationNotificationToTeacher(Long teacherId, Long lectureId, String lectureTitle, String studentName);
    
    /**
     * 发送报名成功通知（包含邮件）
     * @param userId 学生用户ID
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendRegistrationSuccessNotificationWithEmail(Long userId, Long lectureId, String lectureTitle, String startTime, String location);
    
    /**
     * 发送讲座即将开始通知（包含邮件）
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 讲座地点
     */
    void sendUpcomingLectureNotificationWithEmail(Long lectureId, String lectureTitle, String startTime, String location);
    
    /**
     * 发送取消报名通知给学生
     * @param userId 学生用户ID
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     */
    void sendRegistrationCancelNotification(Long userId, Long lectureId, String lectureTitle);
    
    /**
     * 发送学生取消报名通知给教师
     * @param teacherId 教师用户ID
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param studentName 学生姓名
     */
    void sendStudentCancelNotificationToTeacher(Long teacherId, Long lectureId, String lectureTitle, String studentName);
}