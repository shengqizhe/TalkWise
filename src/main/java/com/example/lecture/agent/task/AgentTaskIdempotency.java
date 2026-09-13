package com.example.lecture.agent.task;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 幂等键规则（纯函数，便于单测）。
 *
 * <p>客户端只提供业务语义键（如讲座 ID、随机 UUID），本类负责加上用户维度前缀，
 * 使不同用户的相同业务键互不冲突——与 {@code agent_task.idempotency_key}
 * 注释「同一用户重复提交去重」及唯一索引 {@code uk_agent_task_idempotency} 的语义一致。
 */
public final class AgentTaskIdempotency {

    /** agent_task.idempotency_key 列宽（VARCHAR(100)） */
    public static final int MAX_KEY_LENGTH = 100;

    /** 超长键的哈希前缀标记 */
    static final String HASH_MARKER = ":h";

    private AgentTaskIdempotency() {
    }

    /**
     * 计算落库用的幂等键。
     *
     * @param userId 发起用户 ID
     * @param rawKey 客户端传入的原始幂等键，可空
     * @return 幂等键；原始键为空/空白时返回 {@code null}，表示该任务不参与幂等去重
     */
    public static String resolve(Long userId, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String owner = "u" + (userId == null ? "0" : userId) + ":";
        String trimmed = rawKey.trim();
        String scoped = owner + trimmed;
        if (scoped.length() <= MAX_KEY_LENGTH) {
            return scoped;
        }
        // 超长键改用哈希，长度可控（owner + ":h" + 64 位十六进制 = 最多约 87 字符）
        return owner + HASH_MARKER.substring(1) + sha256Hex(trimmed);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然支持 SHA-256，此处仅做兜底
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
