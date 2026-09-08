package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价实体类
 */
@Data
@TableName("evaluation")
public class Evaluation {
    
    /**
     * 评价ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 讲座ID
     */
    private Long lectureId;
    
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
     * 改进建议(AI生成)
     */
    private String improvementSuggestion;
    
    /**
     * 是否删除(0:未删除 1:已删除)
     */
    @TableLogic
    private Integer deleted;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
} 