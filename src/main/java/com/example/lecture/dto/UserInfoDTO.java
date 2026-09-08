package com.example.lecture.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息DTO
 */
@Data
public class UserInfoDTO {
    
    /**
     * 用户ID
     */
    private Long id;
    
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
     * 头像URL
     */
    private String avatar;
    
    /**
     * 系别ID
     */
    private Long departmentId;
    
    /**
     * 兴趣标签
     */
    private String interestTags;
    
    /**
     * 参与度评分
     */
    private Double participationScore;
    
    /**
     * 用户角色列表
     */
    private List<String> roles;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
} 