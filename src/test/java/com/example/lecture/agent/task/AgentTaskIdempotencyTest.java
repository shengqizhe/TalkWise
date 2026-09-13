package com.example.lecture.agent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等键规则单测（纯函数，不依赖 Spring 上下文）。
 */
class AgentTaskIdempotencyTest {

    @Test
    void blankKeyMeansNoDeduplication() {
        assertNull(AgentTaskIdempotency.resolve(1L, null));
        assertNull(AgentTaskIdempotency.resolve(1L, ""));
        assertNull(AgentTaskIdempotency.resolve(1L, "   "));
    }

    @Test
    void sameUserAndKeyProduceSameScopedKey() {
        String first = AgentTaskIdempotency.resolve(7L, "lecture-12-report");
        String second = AgentTaskIdempotency.resolve(7L, "lecture-12-report");
        assertEquals(first, second);
        assertEquals("u7:lecture-12-report", first);
    }

    @Test
    void differentUsersWithSameBusinessKeyDoNotCollide() {
        String userA = AgentTaskIdempotency.resolve(1L, "daily-report");
        String userB = AgentTaskIdempotency.resolve(2L, "daily-report");
        assertNotEquals(userA, userB);
    }

    @Test
    void keyIsTrimmedAndBoundedToColumnWidth() {
        assertEquals("u1:abc", AgentTaskIdempotency.resolve(1L, "  abc  "));

        String longKey = "x".repeat(300);
        String resolved = AgentTaskIdempotency.resolve(1L, longKey);
        assertTrue(resolved.length() <= AgentTaskIdempotency.MAX_KEY_LENGTH,
                "超长键必须压缩到列宽以内，实际长度=" + resolved.length());
        // 同一超长键长度可控且稳定
        assertEquals(resolved, AgentTaskIdempotency.resolve(1L, longKey));
        // 不同用户的同一超长键仍不冲突
        assertNotEquals(resolved, AgentTaskIdempotency.resolve(2L, longKey));
    }
}
