package com.example.lecture.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报名讲座DTO
 * 用于返回用户报名的讲座信息
 */
@Data
public class RegistrationLectureDTO {
    
    /**
     * 报名ID
     */
    private Long id;
    
    /**
     * 讲座ID
     */
    private Long lectureId;
    
    /**
     * 讲座标题
     */
    private String lectureTitle;
    
    /**
     * 讲座分类名称
     */
    private String categoryName;
    
    /**
     * 主讲人
     */
    private String speaker;
    
    /**
     * 地点
     */
    private String location;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 讲座描述
     */
    private String description;
    
    /**
     * 报名时间
     */
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
     * 讲座发布状态(0:未发布 1:已发布)
     */
    private Integer publishStatus;
    
    /**
     * 讲座状态(1:未开始 2:进行中 3:已结束 4:已取消)
     */
    private Integer lectureStatus;
    
    /**
     * 签到状态(0:未签到 1:已签到)
     */
    private Integer checkinStatus;
}