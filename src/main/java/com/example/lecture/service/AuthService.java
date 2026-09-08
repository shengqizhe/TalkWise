package com.example.lecture.service;

public interface AuthService {
    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return Sa-Token token
     */
    String login(String username, String password);

    /**
     * 用户注册
     * @param username 用户名
     * @param email 邮箱
     * @param password 密码
     */
    void register(String username, String email, String password);

    /**
     * 重置密码
     * @param email 邮箱
     * @param newPassword 新密码
     */
    void resetPassword(String email, String newPassword);
} 