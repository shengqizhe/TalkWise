package com.example.lecture.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价DTO类
 */
@Data
public class EvaluationDTO {
    
    /**
     * 评价ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户姓名
     */
    private String userName;
    
    /**
     * 讲座ID
     */
    private Long lectureId;
    
    /**
     * 讲座标题
     */
    private String lectureTitle;
    
    /**
     * 评分
     */
    private BigDecimal score;
    
    /**
     * 评价内容
     */
    private String content;
    
    /**
     * 情感极性分数
     */
    private BigDecimal sentimentScore;
    
    /**
     * 改进建议
     */
    private String improvementSuggestion;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
} 