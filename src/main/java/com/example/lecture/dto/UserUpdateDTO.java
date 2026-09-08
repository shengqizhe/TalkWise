package com.example.lecture.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户更新DTO
 */
@Data
public class UserUpdateDTO {
    
    /**
     * 真实姓名
     */
    @Size(max = 50, message = "真实姓名长度不能超过50个字符")
    private String realName;
    
    /**
     * 学号/教师编号
     */
    @Size(max = 50, message = "学号/教师编号长度不能超过50个字符")
    private String studentTeacherId;
    
    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 手机号
     */
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    @jakarta.validation.constraints.Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
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
     * 兴趣标签(JSON格式)
     */
    private String interestTags;
}