package com.example.lecture.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.common.api.ResultCode;
import com.example.lecture.entity.Role;
import com.example.lecture.entity.User;
import com.example.lecture.entity.UserRole;
import com.example.lecture.mapper.RoleMapper;
import com.example.lecture.mapper.UserMapper;
import com.example.lecture.mapper.UserRoleMapper;
import com.example.lecture.service.AuthService;
import com.example.lecture.service.UserService;
import com.example.lecture.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务实现类
 */
@Service
public class AuthServiceImpl implements AuthService {

    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UserRoleMapper userRoleMapper;
    
    @Autowired
    private RoleMapper roleMapper;
    
    @Autowired
    private UserService userService;

    @Override
    public String login(String username, String password) {
        // 查询用户
        User user = userService.getByUsername(username);

        // 校验用户存在性和密码（使用PasswordUtil对称加密校验）
        if (user == null || !PasswordUtil.matches(password, user.getPassword())) {
            throw new ApiException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 校验用户是否被删除
        if (user.getDeleted() == 1) {
            throw new ApiException(ResultCode.USER_NOT_EXIST);
        }

        // 登录成功, 生成token
        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    @Override
    @Transactional
    public void register(String username, String email, String password) {
        // 检查用户名是否已存在
        if (userService.getByUsername(username) != null) {
            throw new ApiException(ResultCode.DUPLICATE_USERNAME);
        }
        
        // 检查邮箱是否已存在
        if (userService.getByEmail(email) != null) {
            throw new ApiException(ResultCode.DUPLICATE_EMAIL);
        }

        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setEmail(email); // 设置用户输入的邮箱
        // 使用PasswordUtil对称加密加密密码
        user.setPassword(PasswordUtil.encode(password));
        
        // 设置必填字段的默认值
        user.setRealName(username);
        long sevenDigitTimestamp = System.currentTimeMillis() % 10000000L;
        String formattedTimestamp1 = String.format("%07d", sevenDigitTimestamp);
        user.setStudentTeacherId("S" + formattedTimestamp1); // 生成默认学号
        user.setPhone("138" + String.format("%08d", (int)(Math.random() * 100000000))); // 生成随机手机号
        user.setParticipationScore(new java.math.BigDecimal("0.0")); // 设置默认参与度评分
        user.setDeleted(0); // 设置未删除状态
        user.setCreatedTime(LocalDateTime.now());
        user.setUpdatedTime(LocalDateTime.now());

        // 保存用户
        userService.save(user);
        
        // 查询学生角色ID
        Role studentRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getRoleName, "student")
        );
        
        if (studentRole != null) {
            // 自动分配学生角色
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(studentRole.getId());
            userRoleMapper.insert(userRole);

        }
    }

    @Override
    @Transactional
    public void resetPassword(String email, String newPassword) {
        // 通过邮箱查询用户
        User user = userService.getByEmail(email);

        if (user == null) {
            throw new ApiException(ResultCode.EMAIL_NOT_REGISTERED);
        }

        // 校验用户是否被删除
        if (user.getDeleted() == 1) {
            throw new ApiException(ResultCode.USER_NOT_EXIST);
        }

        // 更新密码（使用PasswordUtil对称加密加密）
        user.setPassword(PasswordUtil.encode(newPassword));
        user.setUpdatedTime(LocalDateTime.now());
        userService.updateById(user);
    }
}