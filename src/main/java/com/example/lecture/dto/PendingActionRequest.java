package com.example.lecture.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PendingActionRequest {
    @NotNull
    private Long actionId;
}
