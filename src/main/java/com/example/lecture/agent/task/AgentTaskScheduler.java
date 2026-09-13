package com.example.lecture.agent.task;

import com.example.lecture.agent.LlmUnavailableException;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.mapper.AgentTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主动任务调度器（方案 A：DB 轮询调度，无需引入消息队列/分布式锁）。
 *
 * <p>职责：
 * <ol>
 *   <li>按 {@code priority DESC, created_time ASC} 取到期待执行任务；</li>
 *   <li>用条件 UPDATE（CAS，PENDING → RUNNING）抢占，抢占失败说明已被其他轮询/实例取走，直接跳过；</li>
 *   <li>抢到的任务提交到通用线程池执行，失败且仍有重试额度时写回 PENDING 并设置退避时间，超限置 FAILED；</li>
 *   <li>应用启动后立即触发一次轮询，使重启前遗留的 PENDING 任务被自动捡起；</li>
 *   <li>停留超时（默认 30 分钟）的 RUNNING 任务按中断处理：有额度重排，无额度判失败。</li>
 * </ol>
 *
 * <p>幂等与一致性说明：
 * <ul>
 *   <li>重复执行的防线在 DB：抢占、重排、终态、进度四处写入都带 status 条件；</li>
 *   <li>进程崩溃遗留的 RUNNING 任务由 stale 恢复兜底，不再永久卡死；</li>
 *   <li>{@link #poll()} 由 Spring 以 fixedDelay 单线程触发，不会与自身并发重叠。</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentTaskScheduler {

    private final AgentTaskMapper taskMapper;
    private final AgentTaskExecutorRegistry executorRegistry;
    private final AgentTaskExecutionSupport support;
    private final Executor taskExecutor;

    /** 单轮扫描条数（防止一次拉起过多任务） */
    @Value("${agent.task.scheduler.batch-size:20}")
    private int batchSize = 20;

    /** 同时在执行的任务数上限（超过则等下一轮） */
    @Value("${agent.task.scheduler.max-concurrent:5}")
    private int maxConcurrent = 5;

    /**
     * 运行中任务的卡死阈值（分钟）：进程崩溃会让任务停在 RUNNING，
     * 超过该时长仍未更新的一律按中断处理（重排或判失败），避免永久卡死。
     */
    @Value("${agent.task.scheduler.stale-running-minutes:30}")
    private int staleRunningMinutes = 30;

    /** 当前在途任务数，用于限制并发（仅本实例内计数） */
    private final AtomicInteger inFlight = new AtomicInteger();

    /**
     * 显式构造器（不用 Lombok）：{@code taskExecutor} 需要 {@link Qualifier} 消除多 Executor Bean 的歧义，
     * 而依赖 Lombok 复制注解需要额外的 lombok.config，显式构造更稳妥。
     */
    public AgentTaskScheduler(AgentTaskMapper taskMapper,
                              AgentTaskExecutorRegistry executorRegistry,
                              AgentTaskExecutionSupport support,
                              @Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskMapper = taskMapper;
        this.executorRegistry = executorRegistry;
        this.support = support;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 调度顺序：优先级高者先执行；同优先级按创建时间早者先执行。
     * 与 selectSchedulable 的 ORDER BY 保持一致，取出后再排一次，保证派发顺序稳定可测。
     */
    public static Comparator<AgentTask> scheduleOrder() {
        return Comparator
                .comparingInt((AgentTask t) -> t.getPriority() == null
                        ? AgentTask.DEFAULT_PRIORITY : t.getPriority())
                .reversed()
                .thenComparing(t -> t.getCreatedTime() == null ? LocalDateTime.MIN : t.getCreatedTime());
    }

    /** 进程启动完成即捡起遗留 PENDING 任务（恢复不依赖下一次定时触发） */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        log.info("主动任务调度器启动，恢复遗留 PENDING 任务与中断的 RUNNING 任务");
        recoverStaleRunning();
        poll();
    }

    /** 轮询入口：默认 3 秒一次（上一轮结束后计时，避免重入堆叠） */
    @Scheduled(fixedDelayString = "${agent.task.scheduler.poll-interval-ms:3000}")
    public void poll() {
        try {
            recoverStaleRunning();
            int capacity = maxConcurrent - inFlight.get();
            if (capacity <= 0) {
                return;
            }
            int limit = Math.min(batchSize, capacity);
            List<AgentTask> candidates = taskMapper.selectSchedulable(LocalDateTime.now(), limit);
            if (candidates.isEmpty()) {
                return;
            }
            candidates.stream().sorted(scheduleOrder()).forEach(this::dispatch);
        } catch (Exception e) {
            log.error("主动任务轮询异常", e);
        }
    }

    /**
     * 恢复卡死的 RUNNING 任务：进程崩溃/被强杀时任务无法自己走向终态。
     * 有重试额度的重排，额度耗尽的判失败，避免任务永久停留在 RUNNING。
     */
    void recoverStaleRunning() {
        if (staleRunningMinutes <= 0) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(staleRunningMinutes);
        try {
            int requeued = taskMapper.recoverStaleRunning(deadline, LocalDateTime.now());
            if (requeued > 0) {
                log.warn("恢复 {} 个中断的运行中任务，已重新排队", requeued);
            }
            int failed = taskMapper.failStaleRunning(deadline, LocalDateTime.now());
            if (failed > 0) {
                log.warn("{} 个中断任务重试次数已用尽，已标记为失败", failed);
            }
        } catch (Exception e) {
            log.error("恢复卡死任务异常", e);
        }
    }

    /** 抢占并提交执行；抢占失败即放弃（任务已被处理或已取消） */
    private void dispatch(AgentTask task) {
        if (task.getId() == null) {
            return;
        }
        if (taskMapper.claimPending(task.getId(), LocalDateTime.now()) != 1) {
            return;
        }
        // 抢占成功：以最新快照执行，避免使用扫描时的过期字段
        AgentTask claimed = taskMapper.selectById(task.getId());
        if (claimed == null) {
            return;
        }
        inFlight.incrementAndGet();
        try {
            taskExecutor.execute(() -> {
                try {
                    executorRegistry.execute(claimed);
                } catch (Exception e) {
                    handleFailure(claimed, e);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RuntimeException e) {
            // 线程池拒绝等提交阶段异常：立即归还调度额度并重排，避免任务卡在 RUNNING
            inFlight.decrementAndGet();
            handleFailure(claimed, e);
        }
    }

    /**
     * 失败处理：仍有重试额度则重排（retryCount+1 + 退避），否则置 FAILED 并通知发起用户。
     * 所有写入都带 status='RUNNING' 条件，因此被取消的任务既不会重排也不会被判失败。
     *
     * <p>包可见以便单测直接覆盖"重排/判失败"的决策分支（依赖注入均为可替换的接口）。
     */
    void handleFailure(AgentTask task, Exception error) {
        Long taskId = task.getId();
        String message = AgentTaskExecutionSupport.truncateError(rootMessage(error));
        log.error("主动任务执行失败（taskId={}, type={}）：{}", taskId, task.getType(), message, error);

        if (AgentTaskRetryPolicy.canRetry(task.getRetryCount(), task.getMaxRetries())) {
            int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            LocalDateTime nextRetryTime = AgentTaskRetryPolicy
                    .nextRetryTime(LocalDateTime.now(), task.getRetryCount(), task.getMaxRetries());
            int updated = taskMapper.releaseForRetry(taskId, nextRetryCount, nextRetryTime, message);
            if (updated == 1) {
                log.info("主动任务 {} 已重排第 {} 次重试，计划执行时间 {}", taskId, nextRetryCount, nextRetryTime);
            }
            return;
        }

        int updated = taskMapper.markFailed(taskId,
                task.getRetryCount() == null ? 0 : task.getRetryCount(), message, LocalDateTime.now());
        if (updated == 1) {
            // 对用户只给统一文案；供应商报文等真实原因已进日志（见上方 log.error）
            support.notifyUser(task.getUserId(), taskId,
                    LlmUnavailableException.isModelFailure(error)
                            ? LlmUnavailableException.USER_MESSAGE
                            : "任务执行失败，请联系管理员排查。");
        }
    }

    /** 取异常链最内层的可读原因，用于落库与日志 */
    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }
}
