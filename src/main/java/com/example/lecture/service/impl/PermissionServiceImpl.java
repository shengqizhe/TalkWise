package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.entity.Permission;
import com.example.lecture.entity.RolePermission;
import com.example.lecture.mapper.PermissionMapper;
import com.example.lecture.mapper.RolePermissionMapper;
import com.example.lecture.mapper.UserRoleMapper;
import com.example.lecture.service.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限Service实现类
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {
    
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    
    public PermissionServiceImpl(RolePermissionMapper rolePermissionMapper, UserRoleMapper userRoleMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }
    
    @Override
    public List<Permission> getPermissionsByRoleId(Long roleId) {
        // 查询角色权限ID列表
        List<Long> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        
        // 查询权限列表
        return listByIds(permissionIds);
    }
    
    @Override
    public List<Permission> getPermissionsByUserId(Long userId) {
        // 查询用户角色ID列表
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        
        // 查询角色权限ID列表
        List<Long> permissionIds = roleIds.stream()
                .flatMap(roleId -> rolePermissionMapper.selectPermissionIdsByRoleId(roleId).stream())
                .distinct()
                .collect(Collectors.toList());
        
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        
        // 查询权限列表
        return listByIds(permissionIds);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolePermissions(Long roleId, List<Long> permissionIds) {
        // 删除原有权限
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        
        // 添加新权限
        List<RolePermission> rolePermissions = permissionIds.stream()
                .map(permissionId -> {
                    RolePermission rolePermission = new RolePermission();
                    rolePermission.setRoleId(roleId);
                    rolePermission.setPermissionId(permissionId);
                    return rolePermission;
                })
                .collect(Collectors.toList());
        
        rolePermissions.forEach(rolePermissionMapper::insert);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRolePermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId)
                .in(RolePermission::getPermissionId, permissionIds));
    }
} 