package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pending_action")
public class PendingAction {
    public static final String TYPE_CREATE_LECTURE = "CREATE_LECTURE";
    public static final String TYPE_UPDATE_LECTURE = "UPDATE_LECTURE";
    public static final String TYPE_CANCEL_LECTURE = "CANCEL_LECTURE";
    public static final String TYPE_PUBLISH_LECTURE = "PUBLISH_LECTURE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String payload;
    private String status;
    private Long resultId;
    /**
     * 目标讲座ID（修改/取消/发布类动作使用；创建类为空）
     */
    private Long targetId;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
