package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.entity.UserRole;
import com.example.lecture.mapper.UserRoleMapper;
import com.example.lecture.service.UserRoleService;
import org.springframework.stereotype.Service;

/**
 * 用户角色关联Service实现类
 */
@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {
    
}