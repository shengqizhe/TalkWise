package com.example.lecture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 异步任务实体（任务型 Agent：大数据量分析转后台执行）
 */
@Data
@TableName("agent_task")
public class AgentTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static final String TYPE_EVALUATION_ANALYSIS = "evaluation_analysis";

    /**
     * 任务ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发起用户ID
     */
    private Long userId;

    /**
     * 任务类型（如 evaluation_analysis）
     */
    private String type;

    /**
     * 任务参数（JSON，如 {"lectureId": 12}）
     */
    private String params;

    /**
     * 状态：PENDING / RUNNING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 进度百分比（0-100）
     */
    private Integer progress;

    /**
     * 进度说明（如"正在分析第 3/5 批"）
     */
    private String progressText;

    /**
     * 任务结果（文本报告）
     */
    private String result;

    /**
     * 失败原因
     */
    private String error;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishedTime;
}
