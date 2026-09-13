package com.example.lecture.agent.task;

import com.example.lecture.entity.AgentTask;
import com.example.lecture.mapper.AgentTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 调度器纯规则单测：调度顺序、CAS 抢占防重复、重试重排、超限转 FAILED。
 *
 * <p>不启动 Spring 上下文：依赖全部以 Mock 注入，只验证调度决策本身。
 */
class AgentTaskSchedulerTest {

    private final AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
    private final AgentTaskExecutorRegistry registry = mock(AgentTaskExecutorRegistry.class);
    private final AgentTaskExecutionSupport support = mock(AgentTaskExecutionSupport.class);
    private final Executor executor = Runnable::run;

    private AgentTaskScheduler scheduler() {
        return new AgentTaskScheduler(taskMapper, registry, support, executor);
    }

    // ---------- 优先级顺序 ----------

    @Test
    void higherPriorityRunsFirstAndTiesBreakByCreatedTime() {
        AgentTask low = task(1L, 0, "2026-09-13T09:00:00");
        AgentTask high = task(2L, 10, "2026-09-13T10:00:00");
        AgentTask highEarlier = task(3L, 10, "2026-09-13T08:00:00");

        List<AgentTask> sorted = List.of(low, high, highEarlier).stream()
                .sorted(AgentTaskScheduler.scheduleOrder())
                .toList();

        assertEquals(List.of(3L, 2L, 1L), sorted.stream().map(AgentTask::getId).toList());
    }

    @Test
    void missingPriorityOrCreatedTimeFallsBackToDefaults() {
        // 优先级缺失按默认 0：因此晚创建的任务仍应排在早创建的任务之后
        AgentTask noPriority = task(1L, null, "2026-09-13T09:00:00");
        AgentTask defaultPriority = task(2L, AgentTask.DEFAULT_PRIORITY, "2026-09-13T10:00:00");
        // 创建时间缺失按最早处理：同优先级下应排最前
        AgentTask noCreatedTime = task(3L, AgentTask.DEFAULT_PRIORITY, null);

        List<AgentTask> sorted = List.of(noCreatedTime, defaultPriority, noPriority).stream()
                .sorted(AgentTaskScheduler.scheduleOrder())
                .toList();

        assertEquals(List.of(3L, 1L, 2L), sorted.stream().map(AgentTask::getId).toList());
    }

    // ---------- CAS 抢占防重复执行 ----------

    @Test
    void skipsTaskWhenCasClaimLosesRace() {
        AgentTask candidate = task(5L, 0, "2026-09-13T09:00:00");
        when(taskMapper.selectSchedulable(any(), anyInt())).thenReturn(List.of(candidate));
        when(taskMapper.claimPending(eq(5L), any())).thenReturn(0); // 已被其他轮询抢走

        scheduler().poll();

        verify(registry, never()).execute(any());
        verify(taskMapper, never()).selectById(any());
    }

    @Test
    void dispatchesClaimedTaskExactlyOnce() {
        AgentTask candidate = task(5L, 0, "2026-09-13T09:00:00");
        AgentTask claimed = task(5L, 0, "2026-09-13T09:00:00");
        claimed.setStatus(AgentTask.STATUS_RUNNING);
        when(taskMapper.selectSchedulable(any(), anyInt())).thenReturn(List.of(candidate));
        when(taskMapper.claimPending(eq(5L), any())).thenReturn(1);
        when(taskMapper.selectById(5L)).thenReturn(claimed);

        scheduler().poll();

        verify(registry, times(1)).execute(claimed);
    }

    // ---------- 失败重排 ----------

    @Test
    void failureUnderLimitRearrangesWithBackoffInsteadOfFailing() {
        AgentTask running = task(9L, 0, "2026-09-13T09:00:00");
        running.setStatus(AgentTask.STATUS_RUNNING);
        running.setRetryCount(0);
        running.setMaxRetries(2);
        when(taskMapper.releaseForRetry(eq(9L), eq(1), any(), anyString())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        scheduler().handleFailure(running, new IllegalStateException("模型超时"));

        ArgumentCaptor<LocalDateTime> nextRetry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskMapper).releaseForRetry(eq(9L), eq(1), nextRetry.capture(), contains("模型超时"));
        verify(taskMapper, never()).markFailed(anyLong(), anyInt(), anyString(), any());
        // 首次重试退避 30s，且时间点落在调用之后
        assertTrue(nextRetry.getValue().isAfter(before.plusSeconds(25)));
    }

    @Test
    void failureAtRetryLimitTransitionsToFailedAndNotifies() {
        AgentTask running = task(9L, 0, "2026-09-13T09:00:00");
        running.setUserId(77L);
        running.setStatus(AgentTask.STATUS_RUNNING);
        running.setRetryCount(2);
        running.setMaxRetries(2);
        when(taskMapper.markFailed(eq(9L), eq(2), anyString(), any())).thenReturn(1);

        scheduler().handleFailure(running, new IllegalStateException("模型超时"));

        verify(taskMapper, never()).releaseForRetry(anyLong(), anyInt(), any(), anyString());
        verify(taskMapper).markFailed(eq(9L), eq(2), contains("模型超时"), any());
        verify(support).notifyUser(eq(77L), eq(9L), contains("执行失败"));
    }

    @Test
    void cancelledTaskIsNeitherRearrangedNorFailed() {
        AgentTask cancelled = task(9L, 0, "2026-09-13T09:00:00");
        cancelled.setUserId(77L);
        cancelled.setRetryCount(0);
        cancelled.setMaxRetries(2);
        // 条件 UPDATE 因状态不匹配而影响 0 行（任务已被取消）
        when(taskMapper.releaseForRetry(eq(9L), eq(1), any(), anyString())).thenReturn(0);
        when(taskMapper.markFailed(eq(9L), anyInt(), anyString(), any())).thenReturn(0);

        scheduler().handleFailure(cancelled, new IllegalStateException("boom"));

        verify(support, never()).notifyUser(any(), any(), anyString());
    }

    private AgentTask task(Long id, Integer priority, String createdTime) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setPriority(priority);
        task.setCreatedTime(createdTime == null ? null : LocalDateTime.parse(createdTime));
        return task;
    }
}
