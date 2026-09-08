package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.entity.Permission;

import java.util.List;

/**
 * 权限Service接口
 */
public interface PermissionService extends IService<Permission> {
    
    /**
     * 根据角色ID查询权限列表
     */
    List<Permission> getPermissionsByRoleId(Long roleId);
    
    /**
     * 根据用户ID查询权限列表
     */
    List<Permission> getPermissionsByUserId(Long userId);
    
    /**
     * 分配角色权限
     */
    void assignRolePermissions(Long roleId, List<Long> permissionIds);
    
    /**
     * 删除角色权限
     */
    void removeRolePermissions(Long roleId, List<Long> permissionIds);
} 