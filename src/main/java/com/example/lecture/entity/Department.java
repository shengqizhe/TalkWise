package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系别实体类
 * 对应数据库表：department
 */
@Data
@TableName("department")
public class Department {
    
    /**
     * 系别ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 系别名称
     */
    private String departmentName;
    
    /**
     * 系别描述
     */
    private String description;
    
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