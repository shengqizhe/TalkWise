package com.example.lecture.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Permission;
import com.example.lecture.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限Controller
 */
@Tag(name = "权限管理", description = "权限相关接口")
@RestController
@RequestMapping("/permission")
public class PermissionController {
    
    private final PermissionService permissionService;
    
    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }
    
    @Operation(summary = "获取权限列表")
    @GetMapping("/list")
    public Result<List<Permission>> list() {
        return Result.success(permissionService.list());
    }
    
    @Operation(summary = "获取角色权限列表")
    @GetMapping("/role/{roleId}")
    public Result<List<Permission>> getPermissionsByRoleId(@PathVariable Long roleId) {
        return Result.success(permissionService.getPermissionsByRoleId(roleId));
    }
    
    @Operation(summary = "获取用户权限列表")
    @GetMapping("/user/{userId}")
    public Result<List<Permission>> getPermissionsByUserId(@PathVariable Long userId) {
        return Result.success(permissionService.getPermissionsByUserId(userId));
    }
    
    @Operation(summary = "分配角色权限")
    @PostMapping("/role/{roleId}")
    @SaCheckRole("admin")
    public Result<Void> assignRolePermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        permissionService.assignRolePermissions(roleId, permissionIds);
        return Result.success();
    }
    
    @Operation(summary = "删除角色权限")
    @DeleteMapping("/role/{roleId}")
    @SaCheckRole("admin")
    public Result<Void> removeRolePermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        permissionService.removeRolePermissions(roleId, permissionIds);
        return Result.success();
    }
} 