package com.example.lecture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 讲座提醒配置类
 * 用于管理讲座即将开始提醒的相关配置参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "lecture.reminder")
public class LectureReminderConfig {
    
    /**
     * 提醒提前时间（分钟）
     * 默认60分钟（1小时）
     */
    private int reminderMinutes = 60;
    
    /**
     * 时间窗口偏移（秒）
     * 用于避免重复发送，默认30秒
     */
    private int windowSeconds = 30;
    
    /**
     * 是否启用邮件提醒
     * 默认启用
     */
    private boolean emailEnabled = true;
    
    /**
     * 是否启用WebSocket提醒
     * 默认启用
     */
    private boolean websocketEnabled = true;
    
    /**
     * 邮件发送失败重试次数
     * 默认不重试
     */
    private int emailRetryCount = 0;
    
    /**
     * 邮件发送超时时间（毫秒）
     * 默认30秒
     */
    private long emailTimeoutMs = 30000;
    
    /**
     * 是否启用详细日志
     * 默认启用
     */
    private boolean verboseLogging = true;
    
    /**
     * 是否启用动态提醒时间
     * 当启用时，提醒时间将根据讲座开始时间动态计算
     * 默认禁用，使用固定的reminderMinutes
     */
    private boolean dynamicReminderEnabled = false;
    
    /**
     * 动态提醒时间规则配置
     * 格式："开始时间范围:提醒分钟数"
     * 例如："morning:30,afternoon:60,evening:120"
     * morning: 06:00-12:00, afternoon: 12:00-18:00, evening: 18:00-24:00, night: 00:00-06:00
     */
    private String dynamicReminderRules = "morning:30,afternoon:60,evening:120,night:60";
    
    /**
     * 最小提醒时间（分钟）
     * 动态计算时的最小值，默认15分钟
     */
    private int minReminderMinutes = 15;
    
    /**
     * 最大提醒时间（分钟）
     * 动态计算时的最大值，默认180分钟（3小时）
     */
    private int maxReminderMinutes = 180;
    
    /**
     * 获取提醒时间窗口开始时间偏移（分钟）
     * @return 开始时间偏移
     */
    public int getStartWindowOffsetMinutes() {
        return reminderMinutes - 1; // 提前1分钟开始窗口
    }
    
    /**
     * 获取提醒时间窗口结束时间偏移（分钟）
     * @return 结束时间偏移
     */
    public int getEndWindowOffsetMinutes() {
        return reminderMinutes + 1; // 延后1分钟结束窗口
    }
    
    /**
     * 根据讲座开始时间动态计算提醒时间
     * @param lectureStartTime 讲座开始时间
     * @return 提醒提前分钟数
     */
    public int calculateDynamicReminderMinutes(java.time.LocalDateTime lectureStartTime) {
        if (!dynamicReminderEnabled) {
            return reminderMinutes; // 如果未启用动态提醒，返回固定值
        }
        
        int hour = lectureStartTime.getHour();
        String timeSlot = getTimeSlot(hour);
        
        // 解析动态提醒规则
        String[] rules = dynamicReminderRules.split(",");
        for (String rule : rules) {
            String[] parts = rule.trim().split(":");
            if (parts.length == 2 && parts[0].trim().equals(timeSlot)) {
                try {
                    int calculatedMinutes = Integer.parseInt(parts[1].trim());
                    // 确保在最小和最大值范围内
                    return Math.max(minReminderMinutes, Math.min(maxReminderMinutes, calculatedMinutes));
                } catch (NumberFormatException e) {
                    // 解析失败，使用默认值
                    break;
                }
            }
        }
        
        // 如果没有匹配的规则，返回默认值
        return reminderMinutes;
    }
    
    /**
     * 根据小时数确定时间段
     * @param hour 小时数 (0-23)
     * @return 时间段标识
     */
    private String getTimeSlot(int hour) {
        if (hour >= 6 && hour < 12) {
            return "morning";   // 上午 06:00-12:00
        } else if (hour >= 12 && hour < 18) {
            return "afternoon"; // 下午 12:00-18:00
        } else if (hour >= 18 && hour < 24) {
            return "evening";   // 晚上 18:00-24:00
        } else {
            return "night";     // 夜间 00:00-06:00
        }
    }
    
    /**
     * 获取动态提醒时间窗口开始时间偏移（分钟）
     * @param lectureStartTime 讲座开始时间
     * @return 开始时间偏移
     */
    public int getDynamicStartWindowOffsetMinutes(java.time.LocalDateTime lectureStartTime) {
        int dynamicMinutes = calculateDynamicReminderMinutes(lectureStartTime);
        return dynamicMinutes - 1; // 提前1分钟开始窗口
    }
    
    /**
     * 获取动态提醒时间窗口结束时间偏移（分钟）
     * @param lectureStartTime 讲座开始时间
     * @return 结束时间偏移
     */
    public int getDynamicEndWindowOffsetMinutes(java.time.LocalDateTime lectureStartTime) {
        int dynamicMinutes = calculateDynamicReminderMinutes(lectureStartTime);
        return dynamicMinutes + 1; // 延后1分钟结束窗口
    }
}