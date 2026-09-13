package com.example.lecture.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.common.api.Result;
import com.example.lecture.dto.AgentTaskCreateRequest;
import com.example.lecture.entity.AgentTask;
import com.example.lecture.service.AgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "主动任务", description = "Agent 主动任务相关接口")
@RestController
@RequestMapping("/agent/tasks")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService taskService;

    @Operation(summary = "分页查询我的任务")
    @GetMapping
    public Result<Page<AgentTask>> page(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(required = false) String status) {
        return Result.success(taskService.page(currentUserId(), current, size, type, status));
    }

    @Operation(summary = "查询任务详情")
    @GetMapping("/{taskId}")
    public Result<AgentTask> get(@PathVariable Long taskId) {
        return Result.success(taskService.get(currentUserId(), taskId));
    }

    @Operation(summary = "创建主动任务")
    @PostMapping
    public Result<AgentTask> create(@Valid @RequestBody AgentTaskCreateRequest request) {
        return Result.success(taskService.create(currentUserId(), request));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable Long taskId) {
        taskService.cancel(currentUserId(), taskId);
        return Result.success();
    }

    private Long currentUserId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsLong();
    }
}
