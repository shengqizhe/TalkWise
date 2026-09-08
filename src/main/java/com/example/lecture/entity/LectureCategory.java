package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 讲座类别实体类
 */
@Data
@TableName("lecture_category")
public class LectureCategory {
    
    /**
     * 类别ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 父类别ID
     */
    private Long parentId;
    
    /**
     * 类别名称
     */
    private String categoryName;
    
    /**
     * 级别(1:学科大类 2:研究方向 3:主题)
     */
    private Integer level;
    
    /**
     * 热度评分
     */
    private Double heatScore;
    
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