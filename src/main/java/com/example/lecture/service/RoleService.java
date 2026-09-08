package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.entity.Role;

import java.util.List;

/**
 * 角色Service接口
 */
public interface RoleService extends IService<Role> {
    
    /**
     * 根据用户ID查询角色列表
     */
    List<Role> getRolesByUserId(Long userId);
    
    /**
     * 分配用户角色
     */
    void assignUserRoles(Long userId, List<Long> roleIds);
    
    /**
     * 删除用户角色
     */
    void removeUserRoles(Long userId, List<Long> roleIds);
} 