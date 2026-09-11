package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报名记录实体类
 */
@Data
@TableName("registration")
public class Registration {
    
    /**
     * 报名ID
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
     * 报名时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime registerTime;
    
    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;
    
    /**
     * 状态(1:已报名 2:已取消)
     */
    private Integer status;
    
    /**
     * 推荐理由(AI生成)
     */
    private String recommendReason;
    
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
     * 签到状态(0:未签到 1:已签到)
     */
    private Integer checkinStatus;

    /**
     * 签到时间
     */
    private LocalDateTime checkinTime;

}