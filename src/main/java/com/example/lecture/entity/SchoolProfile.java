package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学校画像实体类，对应数据库表：school_profile。
 */
@Data
@TableName("school_profile")
public class SchoolProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String schoolName;

    /** 学校办学定位、优势学科与学生群体画像 */
    private String profileContent;

    private Integer minRoomCapacity;

    private Integer maxRoomCapacity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
