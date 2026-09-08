package com.example.lecture.controller;

import com.example.lecture.dto.AiAssistantRequestDTO;
import com.example.lecture.dto.AiAssistantResponseDTO;
import com.example.lecture.service.AiAssistantService;
import com.example.lecture.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI助手", description = "AI智能助手相关接口")
@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    @Autowired
    private AiAssistantService aiAssistantService;

    @Operation(summary = "AI对话")
    @PostMapping("/assistant")
    public Result<AiAssistantResponseDTO> chat(@RequestBody AiAssistantRequestDTO request) {
        return Result.success(aiAssistantService.chat(request));
    }
    
    @Operation(summary = "获取用户已报名的讲座")
    @GetMapping("/registrations/{userId}")
    public Result<AiAssistantResponseDTO> getUserRegistrations(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        return Result.success(aiAssistantService.getUserRegistrations(userId));
    }
    
    @Operation(summary = "取消用户报名")
    @PostMapping("/cancel-registration")
    public Result<AiAssistantResponseDTO> cancelUserRegistration(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "讲座ID") @RequestParam Long lectureId) {
        return Result.success(aiAssistantService.cancelUserRegistration(userId, lectureId));
    }
}