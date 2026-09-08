package com.example.lecture.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报名用户DTO
 * 用于返回讲座报名用户信息
 */
@Data
public class RegistrationUserDTO {
    
    /**
     * 报名ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 真实姓名
     */
    private String realName;
    
    /**
     * 学号/教师编号
     */
    private String studentTeacherId;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 系别ID
     */
    private Long departmentId;
    
    /**
     * 系别名称
     */
    private String departmentName;
    
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