package com.example.lecture.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应数据库表：user
 */
@Data
@TableName("user")
public class User {

    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @ExcelProperty(value = "用户名")
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 真实姓名
     */
    @ExcelProperty(value = "姓名")
    private String realName;

    /**
     * 学号/教师编号
     */
    @ExcelProperty(value = "学号")
    private String studentTeacherId;

    /**
     * 邮箱
     */
    @ExcelProperty(value = "邮箱")
    private String email;

    /**
     * 手机号
     */
    @ExcelProperty(value = "电话")
    @jakarta.validation.constraints.Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 系别ID
     */
    @ExcelProperty(value = "系别ID")
    private Long departmentId;

    /**
     * 头像URL
     */
    private String avatar;


    /**
     * 兴趣标签(JSON格式)
     */
    private String interestTags;

    /**
     * 参与度评分
     */
    private BigDecimal participationScore;

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