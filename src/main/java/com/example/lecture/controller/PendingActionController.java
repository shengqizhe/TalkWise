package com.example.lecture.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.lecture.common.api.Result;
import com.example.lecture.dto.PendingActionResponse;
import com.example.lecture.service.PendingActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent/actions")
@RequiredArgsConstructor
public class PendingActionController {
    private final PendingActionService pendingActionService;

    @PostMapping("/{id}/confirm")
    public Result<PendingActionResponse> confirm(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(pendingActionService.confirm(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/{id}/reject")
    public Result<PendingActionResponse> reject(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(pendingActionService.reject(StpUtil.getLoginIdAsLong(), id));
    }
}
