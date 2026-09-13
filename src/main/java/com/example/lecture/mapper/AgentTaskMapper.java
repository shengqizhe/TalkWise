package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 异步任务 Mapper
 *
 * <p>调度相关方法统一使用条件 UPDATE（CAS）保证多实例/多轮询并发下不重复执行：
 * 抢占只认 {@code status='PENDING'}，重排/终态只认 {@code status='RUNNING'}。
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    /**
     * 取待执行任务：按优先级降序、创建时间升序；重试退避未到期的跳过。
     * 走索引 idx_agent_task_schedule(status, priority, next_retry_time, created_time)。
     *
     * @param now   当前时间（用于判断 next_retry_time 是否到期）
     * @param limit 单轮最多取几条
     */
    @Select("SELECT * FROM agent_task "
            + "WHERE status = 'PENDING' AND (next_retry_time IS NULL OR next_retry_time <= #{now}) "
            + "ORDER BY priority DESC, created_time ASC "
            + "LIMIT #{limit}")
    List<AgentTask> selectSchedulable(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * CAS 抢占任务：仅当仍为 PENDING 时置为 RUNNING，防止同一任务被重复执行。
     *
     * @return 影响行数，1 表示抢到，0 表示已被其他调度抢走/已取消
     */
    @Update("UPDATE agent_task SET status = 'RUNNING', started_time = #{startedTime}, "
            + "progress_text = '执行中', next_retry_time = NULL "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int claimPending(@Param("id") Long id, @Param("startedTime") LocalDateTime startedTime);

    /**
     * 执行失败重排：retry_count+1、置回 PENDING、写入退避后的 next_retry_time。
     * 仅当任务仍为 RUNNING 时生效，已取消的任务不会被重排。
     */
    @Update("UPDATE agent_task SET status = 'PENDING', retry_count = #{retryCount}, "
            + "next_retry_time = #{nextRetryTime}, error = #{error}, "
            + "progress_text = '等待重试', progress = 0, finished_time = NULL "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int releaseForRetry(@Param("id") Long id,
                        @Param("retryCount") int retryCount,
                        @Param("nextRetryTime") LocalDateTime nextRetryTime,
                        @Param("error") String error);

    /**
     * 重试次数耗尽：置为 FAILED 终态。仅当任务仍为 RUNNING 时生效。
     */
    @Update("UPDATE agent_task SET status = 'FAILED', retry_count = #{retryCount}, "
            + "progress_text = '已失败', error = #{error}, finished_time = #{finishedTime} "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int markFailed(@Param("id") Long id,
                   @Param("retryCount") int retryCount,
                   @Param("error") String error,
                   @Param("finishedTime") LocalDateTime finishedTime);

    /** 按状态统计（运维观测用） */
    @Select("SELECT COUNT(*) FROM agent_task WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 恢复卡死的运行中任务：进程崩溃会让任务停留在 RUNNING 且 started_time 不再更新。
     * 超过阈值仍为 RUNNING 的任务按可重试额度重排，避免永久卡死。
     *
     * @param deadline 早于该时间视为卡死
     * @return 受影响行数
     */
    @Update("UPDATE agent_task SET status = 'PENDING', "
            + "retry_count = CASE WHEN retry_count < max_retries THEN retry_count + 1 ELSE retry_count END, "
            + "next_retry_time = #{nextRetryTime}, progress_text = '等待重试（上次执行超时/中断）', "
            + "error = '任务中断，已自动重排' "
            + "WHERE status = 'RUNNING' AND started_time IS NOT NULL AND started_time < #{deadline} "
            + "AND retry_count < max_retries")
    int recoverStaleRunning(@Param("deadline") LocalDateTime deadline,
                            @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /**
     * 卡死且重试额度已耗尽的任务置为 FAILED。
     *
     * @param deadline      早于该时间视为卡死
     * @param finishedTime  失败时间
     * @return 受影响行数
     */
    @Update("UPDATE agent_task SET status = 'FAILED', progress_text = '已失败', "
            + "error = '任务中断且重试次数已用尽', finished_time = #{finishedTime} "
            + "WHERE status = 'RUNNING' AND started_time IS NOT NULL AND started_time < #{deadline} "
            + "AND retry_count >= max_retries")
    int failStaleRunning(@Param("deadline") LocalDateTime deadline,
                         @Param("finishedTime") LocalDateTime finishedTime);

    /**
     * 进度上报：仅当任务仍为 RUNNING 时写入，避免把已取消任务改回运行中。
     */
    @Update("UPDATE agent_task SET progress = #{progress}, progress_text = #{progressText}, "
            + "started_time = COALESCE(started_time, #{startedTime}) "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int updateProgress(@Param("id") Long id,
                       @Param("progress") int progress,
                       @Param("progressText") String progressText,
                       @Param("startedTime") LocalDateTime startedTime);

    /**
     * 成功终态：仅当任务仍为 RUNNING 时写入，已取消任务保持 CANCELLED。
     */
    @Update("UPDATE agent_task SET status = 'SUCCESS', progress = 100, progress_text = '已完成', "
            + "result = #{result}, finished_time = #{finishedTime} "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int markSuccess(@Param("id") Long id,
                    @Param("result") String result,
                    @Param("finishedTime") LocalDateTime finishedTime);
}
