package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * 讲座实体类
 */
@Data
@TableName("lecture")
public class Lecture {
    
    /**
     * 讲座ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 摘要
     */
    private String summary;
    /**
     * 讲座宣传内容(图片)
     */
    private String promotionContent;
    /**
     * 内容
     */
    private String content;
    
    /**
     * SEO标题(AI生成)
     */
    private String seoTitle;
    
    /**
     * 关键词(AI生成)
     */
    private String keywords;
    
    /**
     * 类别ID
     */
    private Long categoryId;
    
    /**
     * 类别名称（非数据库字段）
     */
    @TableField(exist = false)
    private String categoryName;
    
    /**
     * 主讲人
     */
    private String speaker;
    
    /**
     * 关联的地点ID
     */
    private Long locationId;
    /**
     * 讲座时间
     */
    private LocalDateTime lectureTime;
    
    /**
     * 容量
     */
    private Integer capacity;
    
    /**
     * 报名人数
     */
    private Integer registeredCount;
    
    /**
     * 组织者ID
     */
    private Long organizerId;
    
    /**
     * 状态(1:未开始 2:进行中 3:已结束 4:已取消)
     */
    private Integer status;
    
    /**
     * 发布状态(0:未发布 1:已发布)
     */
    private Integer publishStatus;
    
    /**
     * 推荐分数
     */
    private Double recommendScore;
    
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

    /**
     * 扩展数据（非数据库字段）
     * 用于存储额外的信息，如数据变化标志等
     */
    @TableField(exist = false)
    private Map<String, Object> extendData = new HashMap<>();
}