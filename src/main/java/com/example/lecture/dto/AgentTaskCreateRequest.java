package com.example.lecture.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentTaskCreateRequest {
    @NotBlank
    private String type;
    private String name;
    private String params;
}
