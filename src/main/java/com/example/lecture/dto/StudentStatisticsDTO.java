package com.example.lecture.dto;

import lombok.Data;

/**
 * 学生统计数据DTO
 */
@Data
public class StudentStatisticsDTO {
    
    /**
     * 可报名讲座数量（未开始且已发布，且用户未报名的讲座）
     */
    private Integer totalLectures;
    
    /**
     * 已报名讲座数量
     */
    private Integer registeredLectures;
    
    /**
     * 已参加讲座数量
     */
    private Integer attendedLectures;
    
    /**
     * 参与度评分
     */
    private Integer participationScore;
}