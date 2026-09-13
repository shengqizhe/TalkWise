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
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String TYPE_EVALUATION_ANALYSIS = "evaluation_analysis";
    public static final String TYPE_REPORT_GENERATION = "report_generation";
    public static final String TYPE_REMINDER_NOTIFICATION = "reminder_notification";

    /** 默认最大重试次数（与 agent_task.max_retries 列默认值一致） */
    public static final int DEFAULT_MAX_RETRIES = 2;
    /** 默认优先级（与 agent_task.priority 列默认值一致） */
    public static final int DEFAULT_PRIORITY = 0;
    /** 请求可声明的最大重试次数上限（防单任务长期占用调度资源） */
    public static final int MAX_RETRIES_LIMIT = 10;

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
     * 幂等键（同一用户重复提交去重；唯一索引 uk_agent_task_idempotency）
     */
    private String idempotencyKey;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务参数（JSON，如 {"lectureId": 12}）
     */
    private String params;

    /**
     * 任务优先级（越大越优先）
     */
    private Integer priority;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 下次可执行时间（失败退避重排，为空表示可立即执行）
     */
    private LocalDateTime nextRetryTime;

    /**
     * 状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED
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
     * 开始执行时间
     */
    private LocalDateTime startedTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishedTime;
}
