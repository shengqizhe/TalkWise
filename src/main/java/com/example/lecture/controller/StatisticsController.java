package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.dto.StudentStatisticsDTO;
import com.example.lecture.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 统计数据Controller
 */
@Tag(name = "统计数据", description = "统计数据相关接口")
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @Operation(summary = "获取学生统计数据")
    @GetMapping("/student/{userId}")
    public Result<StudentStatisticsDTO> getStudentStatistics(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        StudentStatisticsDTO statistics = statisticsService.getStudentStatistics(userId);
        return Result.success(statistics);
    }
}