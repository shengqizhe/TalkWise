package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 地点实体类
 * 对应数据库表：location
 */
@Data
@TableName("location")
public class Location {
    
    /**
     * 地点ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 地点名称
     */
    private String name;
    
    /**
     * 经度
     */
    private BigDecimal longitude;
    
    /**
     * 纬度
     */
    private BigDecimal latitude;
    
    /**
     * 地点类型（如building）
     */
    private String type;
    
    /**
     * 详细地址（可选）
     */
    private String address;
    
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