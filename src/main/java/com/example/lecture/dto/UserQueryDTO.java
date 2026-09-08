package com.example.lecture.dto;

import lombok.Data;

/**
 * 用户查询DTO
 */
@Data
public class UserQueryDTO {
    
    /**
     * 用户名（模糊查询）
     */
    private String username;
    
    /**
     * 真实姓名（模糊查询）
     */
    private String realName;
    
    /**
     * 学号/教师编号（模糊查询）
     */
    private String studentTeacherId;
    
    /**
     * 邮箱（模糊查询）
     */
    private String email;
    
    /**
     * 系别ID
     */
    private Long departmentId;
    
    /**
     * 角色名称
     */
    private String roleName;
    
    /**
     * 当前页码
     */
    private Integer current = 1;
    
    /**
     * 每页大小
     */
    private Integer size = 10;
} 