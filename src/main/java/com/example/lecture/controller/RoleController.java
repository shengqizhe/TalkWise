package com.example.lecture.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Role;
import com.example.lecture.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色Controller
 */
@Tag(name = "角色管理", description = "角色相关接口")
@RestController
@RequestMapping("/role")
public class RoleController {
    
    private final RoleService roleService;
    
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }
    
    @Operation(summary = "获取角色列表")
    @GetMapping("/list")
    public Result<List<Role>> list() {
        return Result.success(roleService.list());
    }
    
    @Operation(summary = "获取用户角色列表")
    @GetMapping("/user/{userId}")
    public Result<List<Role>> getRolesByUserId(@PathVariable Long userId) {
        return Result.success(roleService.getRolesByUserId(userId));
    }
    
    @Operation(summary = "分配用户角色")
    @PostMapping("/user/{userId}")
    @SaCheckRole("admin")
    public Result<Void> assignUserRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        roleService.assignUserRoles(userId, roleIds);
        return Result.success();
    }
    
    @Operation(summary = "删除用户角色")
    @DeleteMapping("/user/{userId}")
    @SaCheckRole("admin")
    public Result<Void> removeUserRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        roleService.removeUserRoles(userId, roleIds);
        return Result.success();
    }
} 