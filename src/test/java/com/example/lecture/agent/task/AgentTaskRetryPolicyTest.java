package com.example.lecture.agent.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重试与退避规则单测（纯函数，不依赖 Spring 上下文、不连数据库）。
 */
class AgentTaskRetryPolicyTest {

    @Test
    void allowsRetryWhileUnderLimitAndStopsAtLimit() {
        assertTrue(AgentTaskRetryPolicy.canRetry(0, 2));
        assertTrue(AgentTaskRetryPolicy.canRetry(1, 2));
        assertFalse(AgentTaskRetryPolicy.canRetry(2, 2));
        assertFalse(AgentTaskRetryPolicy.canRetry(3, 2));
    }

    @Test
    void zeroMaxRetriesMeansNoRetry() {
        assertFalse(AgentTaskRetryPolicy.canRetry(0, 0));
    }

    @Test
    void nullCountersFallBackToDefaults() {
        // 计数为空按 0 处理，额度为空按默认 2 处理
        assertTrue(AgentTaskRetryPolicy.canRetry(null, null));
        assertEquals(2, com.example.lecture.entity.AgentTask.DEFAULT_MAX_RETRIES);
    }

    @Test
    void backoffDoublesPerAttemptAndIsCapped() {
        assertEquals(Duration.ofSeconds(30), AgentTaskRetryPolicy.backoff(1));
        assertEquals(Duration.ofSeconds(60), AgentTaskRetryPolicy.backoff(2));
        assertEquals(Duration.ofSeconds(120), AgentTaskRetryPolicy.backoff(3));
        assertEquals(Duration.ofSeconds(240), AgentTaskRetryPolicy.backoff(4));
        assertEquals(Duration.ofSeconds(480), AgentTaskRetryPolicy.backoff(5));
        // 翻倍超过上限后被截断，且不再增长（防溢出）
        assertEquals(AgentTaskRetryPolicy.MAX_BACKOFF, AgentTaskRetryPolicy.backoff(6));
        assertEquals(AgentTaskRetryPolicy.MAX_BACKOFF, AgentTaskRetryPolicy.backoff(100));
    }

    @Test
    void nextRetryTimeUsesBackoffOfUpcomingAttempt() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0, 0);

        // 首次失败（retryCount=0）→ 第 1 次重试，退避 30s
        assertEquals(now.plusSeconds(30), AgentTaskRetryPolicy.nextRetryTime(now, 0, 2));
        // 第二次失败（retryCount=1）→ 第 2 次重试，退避 60s
        assertEquals(now.plusSeconds(60), AgentTaskRetryPolicy.nextRetryTime(now, 1, 2));
    }

    @Test
    void noNextRetryTimeWhenQuotaExhausted() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0, 0);
        assertNull(AgentTaskRetryPolicy.nextRetryTime(now, 2, 2));
    }
}
