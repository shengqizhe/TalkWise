package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.dto.UserInfoDTO;
import com.example.lecture.dto.UserQueryDTO;
import com.example.lecture.entity.User;

import java.util.List;

/**
 * 用户Service接口
 * 继承IService<User>，已包含批量保存（saveBatch）、条件查询（list）等常用方法
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户
     */
    User getByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    User getByEmail(String email);

    /**
     * 根据手机号查询用户
     */
    User getByPhone(String phone);

    /**
     * 根据系别ID查询用户列表
     */
    List<User> getUsersByDepartmentId(Long departmentId);

    /**
     * 分页查询用户
     */
    Page<User> getUserPage(UserQueryDTO queryDTO);

    /**
     * 用户注册
     */
    void register(User user);

    /**
     * 用户登录
     */
    String login(String username, String password);

    /**
     * 更新用户信息
     */
    void updateUser(User user);

    /**
     * 修改密码
     */
    void updatePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 获取用户信息（包含角色）
     */
    UserInfoDTO getUserInfo(Long userId);

    /**
     * 根据学号查询用户是否存在
     */
    boolean existsByStudentTeacherId(String studentTeacherId);
}