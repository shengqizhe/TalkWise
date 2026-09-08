package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.dto.RegistrationLectureDTO;
import com.example.lecture.dto.RegistrationUserDTO;
import com.example.lecture.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报名记录Controller
 */
@Tag(name = "报名管理", description = "讲座报名相关接口")
@RestController
@RequestMapping("/registration")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @Operation(summary = "报名讲座")
    @PostMapping("/register")
    public Result<Void> register(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "讲座ID") @RequestParam Long lectureId) {
        registrationService.register(userId, lectureId);
        return Result.success();
    }

    @Operation(summary = "取消报名")
    @PostMapping("/cancel")
    public Result<Void> cancel(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "讲座ID") @RequestParam Long lectureId) {
        registrationService.cancel(userId, lectureId);
        return Result.success();
    }

    @Operation(summary = "检查是否已报名")
    @GetMapping("/check")
    public Result<Boolean> checkRegistration(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "讲座ID") @RequestParam Long lectureId) {
        boolean isRegistered = registrationService.isRegistered(userId, lectureId);
        return Result.success(isRegistered);
    }
    
    @Operation(summary = "获取讲座报名列表")
    @GetMapping("/lecture/{lectureId}")
    public Result<List<RegistrationUserDTO>> getLectureRegistrations(
            @Parameter(description = "讲座ID") @PathVariable Long lectureId) {
        List<RegistrationUserDTO> registrations = registrationService.getLectureRegistrations(lectureId);
        return Result.success(registrations);
    }
    
    @Operation(summary = "获取用户报名列表")
    @GetMapping("/user/{userId}")
    public Result<List<RegistrationLectureDTO>> getUserRegistrations(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            @Parameter(description = "状态(可选): 1-已报名, 2-已取消") @RequestParam(required = false) Integer status) {
        List<RegistrationLectureDTO> registrations = registrationService.getUserRegistrations(userId, status);
        return Result.success(registrations);
    }
    
    @Operation(summary = "学生签到")
    @PostMapping("/checkin")
    public Result<Void> checkin(
            @Parameter(description = "报名记录ID") @RequestParam Long registrationId) {
        registrationService.checkin(registrationId);
        return Result.success();
    }
    
    @Operation(summary = "取消签到")
    @PostMapping("/cancel-checkin")
    public Result<Void> cancelCheckin(
            @Parameter(description = "报名记录ID") @RequestParam Long registrationId) {
        registrationService.cancelCheckin(registrationId);
        return Result.success();
    }
}