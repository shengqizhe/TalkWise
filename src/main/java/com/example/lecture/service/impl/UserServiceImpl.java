package com.example.lecture.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.common.api.ResultCode;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.dto.UserInfoDTO;
import com.example.lecture.dto.UserQueryDTO;
import com.example.lecture.entity.Department;
import com.example.lecture.entity.Role;
import com.example.lecture.entity.User;
import com.example.lecture.entity.UserRole;
import com.example.lecture.mapper.DepartmentMapper;
import com.example.lecture.mapper.RoleMapper;
import com.example.lecture.mapper.UserMapper;
import com.example.lecture.mapper.UserRoleMapper;
import com.example.lecture.service.RoleService;
import com.example.lecture.service.UserService;
import com.example.lecture.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户Service实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private RoleService roleService;

    @Override
    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    @Override
    public User getByEmail(String email) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
    }

    @Override
    public User getByPhone(String phone) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
    }

    @Override
    public List<User> getUsersByDepartmentId(Long departmentId) {
        return list(new LambdaQueryWrapper<User>()
                .eq(User::getDepartmentId, departmentId)
                .orderByAsc(User::getRealName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(User user) {
        // 检查用户名是否已存在
        if (getByUsername(user.getUsername()) != null) {
            throw new ApiException(ResultCode.DUPLICATE_USERNAME);
        }

        // 检查邮箱是否已存在
        if (user.getEmail() != null && getByEmail(user.getEmail()) != null) {
            throw new ApiException(ResultCode.DUPLICATE_EMAIL);
        }

        // 检查手机号是否已存在
        if (user.getPhone() != null && !user.getPhone().isEmpty() && getByPhone(user.getPhone()) != null) {
            throw new ApiException(ResultCode.DUPLICATE_PHONE);
        }

        // 验证系别是否存在
        if (user.getDepartmentId() != null) {
            Department department = departmentMapper.selectById(user.getDepartmentId());
            if (department == null) {
                throw new ApiException(ResultCode.DEPARTMENT_NOT_EXIST);
            }
        }

        // 加密密码
        user.setPassword(PasswordUtil.encode(user.getPassword()));

        // 保存用户
        save(user);

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
    public String login(String username, String password) {
        // 查询用户
        User user = getByUsername(username);
        if (user == null) {
            throw new ApiException(ResultCode.USER_NOT_EXIST);
        }

        // 验证密码
        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw new ApiException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 使用Sa-Token进行登录
        StpUtil.login(user.getId());

        // 返回token
        return StpUtil.getTokenValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(User user) {
        // 检查用户是否存在
        User existingUser = getById(user.getId());
        if (existingUser == null) {
            throw new ApiException(ResultCode.USER_NOT_EXIST);
        }

        // 检查手机号是否已被其他用户使用
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            User userWithPhone = getByPhone(user.getPhone());
            if (userWithPhone != null && !userWithPhone.getId().equals(user.getId())) {
                throw new ApiException(ResultCode.DUPLICATE_PHONE);
            }
        }

        // 验证系别是否存在
        if (user.getDepartmentId() != null) {
            Department department = departmentMapper.selectById(user.getDepartmentId());
            if (department == null) {
                throw new ApiException(ResultCode.DEPARTMENT_NOT_EXIST);
            }
        }

        // 更新用户信息
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        // 查询用户
        User user = getById(userId);
        if (user == null) {
            throw new ApiException(ResultCode.USER_NOT_EXIST);
        }

        // 验证旧密码
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            throw new ApiException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 更新密码
        user.setPassword(PasswordUtil.encode(newPassword));
        updateById(user);

        // 修改密码后，使当前用户的所有token失效
        StpUtil.logout(userId);
    }

    @Override
    public UserInfoDTO getUserInfo(Long userId) {
        // 查询用户基本信息
        User user = getById(userId);
        if (user == null) {
            throw new ApiException(ResultCode.USER_NOT_EXIST);
        }

        // 查询用户角色
        List<Role> roles = roleService.getRolesByUserId(userId);
        List<String> roleNames = roles.stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());

        // 构建用户信息DTO
        UserInfoDTO userInfoDTO = new UserInfoDTO();
        userInfoDTO.setId(user.getId());
        userInfoDTO.setUsername(user.getUsername());
        userInfoDTO.setRealName(user.getRealName());
        userInfoDTO.setStudentTeacherId(user.getStudentTeacherId());
        userInfoDTO.setEmail(user.getEmail());
        userInfoDTO.setPhone(user.getPhone());
        userInfoDTO.setAvatar(user.getAvatar());
        userInfoDTO.setDepartmentId(user.getDepartmentId());
        userInfoDTO.setInterestTags(user.getInterestTags());
        userInfoDTO.setParticipationScore(user.getParticipationScore() != null ?
                user.getParticipationScore().doubleValue() : null);
        userInfoDTO.setRoles(roleNames);
        userInfoDTO.setCreatedTime(user.getCreatedTime());
        userInfoDTO.setUpdatedTime(user.getUpdatedTime());

        return userInfoDTO;
    }

    @Override
    public Page<User> getUserPage(UserQueryDTO userQueryDTO) {
        // 创建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        // 根据查询条件设置查询条件
        if (StringUtils.hasText(userQueryDTO.getUsername())) {
            queryWrapper.like(User::getUsername, userQueryDTO.getUsername());
        }
        if (StringUtils.hasText(userQueryDTO.getRealName())) {
            queryWrapper.like(User::getRealName, userQueryDTO.getRealName());
        }
        if (userQueryDTO.getDepartmentId() != null) {
            queryWrapper.eq(User::getDepartmentId, userQueryDTO.getDepartmentId());
        }
        if (userQueryDTO.getStudentTeacherId() != null) {
            queryWrapper.eq(User::getStudentTeacherId, userQueryDTO.getStudentTeacherId());
        }

        // 创建分页对象
        Page<User> page = new Page<>(userQueryDTO.getCurrent(), userQueryDTO.getSize());

        // 执行查询
        return page(page, queryWrapper);
    }

    /**
     * 判断学号是否存在
     * @param studentTeacherId 学号
     * @return 存在返回true，否则false
     */
    @Override
    public boolean existsByStudentTeacherId(String studentTeacherId) {
        return count(new LambdaQueryWrapper<User>().eq(User::getStudentTeacherId, studentTeacherId)) > 0;
    }
}