package com.example.lecture.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentTaskCreateRequest {
    @NotBlank
    private String type;
    private String name;
    private String params;

    /** 幂等键：同一用户相同键重复提交时直接返回已有任务；为空则不做幂等去重 */
    private String idempotencyKey;

    /** 任务优先级（越大越优先），为空时取默认 0 */
    private Integer priority;

    /** 最大重试次数（0 表示不重试），为空时取默认 2，超过上限会被钳制 */
    private Integer maxRetries;
}
