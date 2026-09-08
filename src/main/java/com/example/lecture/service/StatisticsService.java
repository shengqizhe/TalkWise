package com.example.lecture.service;

import com.example.lecture.dto.StudentStatisticsDTO;

/**
 * 统计数据Service接口
 */
public interface StatisticsService {
    
    /**
     * 获取学生统计数据
     * @param userId 用户ID
     * @return 学生统计数据
     */
    StudentStatisticsDTO getStudentStatistics(Long userId);
}