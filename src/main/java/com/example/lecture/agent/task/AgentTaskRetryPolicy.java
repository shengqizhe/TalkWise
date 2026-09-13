package com.example.lecture.agent.task;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 任务重试规则（纯函数，便于单测）。
 *
 * <p>退避策略：第 n 次重试（n 从 1 开始）等待 {@code base * 2^(n-1)}，并按上限截断。
 * 例如 base=30s、上限=600s 时：30s、60s、120s、240s、480s、600s、600s…
 */
public final class AgentTaskRetryPolicy {

    /** 首次重试的基础退避 */
    public static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

    /** 单次退避上限（避免任务被无限推迟） */
    public static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private AgentTaskRetryPolicy() {
    }

    /**
     * 第 retryNo 次重试（1 起始）的退避时长。
     */
    public static Duration backoff(int retryNo) {
        if (retryNo <= 1) {
            return BASE_BACKOFF;
        }
        // 逐次翻倍；超过上限直接返回上限，避免位移溢出
        Duration delay = BASE_BACKOFF;
        for (int i = 1; i < retryNo; i++) {
            delay = delay.multipliedBy(2);
            if (delay.compareTo(MAX_BACKOFF) >= 0) {
                return MAX_BACKOFF;
            }
        }
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    /**
     * 本次失败后是否还能重试。
     *
     * @param retryCount 已重试次数
     * @param maxRetries 最大重试次数
     */
    public static boolean canRetry(Integer retryCount, Integer maxRetries) {
        int used = retryCount == null ? 0 : retryCount;
        int limit = maxRetries == null
                ? com.example.lecture.entity.AgentTask.DEFAULT_MAX_RETRIES
                : maxRetries;
        return used < limit;
    }

    /**
     * 计算下一次可执行时间。
     *
     * @param now      当前时间
     * @param retryCount 已重试次数（未递增前）
     * @param maxRetries 最大重试次数
     * @return 下次可执行时间；已无重试额度时返回 {@code null}
     */
    public static LocalDateTime nextRetryTime(LocalDateTime now, Integer retryCount, Integer maxRetries) {
        if (!canRetry(retryCount, maxRetries)) {
            return null;
        }
        int nextRetryNo = (retryCount == null ? 0 : retryCount) + 1;
        return now.plus(backoff(nextRetryNo));
    }
}
