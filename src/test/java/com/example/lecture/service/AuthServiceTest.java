package com.example.lecture.service;

import com.example.lecture.common.exception.ApiException;
import com.example.lecture.entity.User;
import com.example.lecture.util.PasswordUtil;
import com.example.university_lecture_management_system.UniversityLectureManagementSystemApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = UniversityLectureManagementSystemApplication.class)
@DisplayName("认证服务集成测试")
@Transactional
@Rollback
@ContextConfiguration(classes = UniversityLectureManagementSystemApplication.class)
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("测试用户注册和登录的完整流程")
    void testRegisterAndLoginSuccess() {
        // 注册一个新用户
        String username = "testuser_auth";
        String email = "test_auth@example.com";
        String password = "password123";
        authService.register(username, email, password);

        // 验证用户已成功创建
        User registeredUser = userService.getByUsername(username);
        assertNotNull(registeredUser, "注册后用户应存在于数据库中");
        assertEquals(email, registeredUser.getEmail());

        // 使用正确的凭据登录
        String token = authService.login(username, password);
        assertNotNull(token, "登录成功应该返回Token");
        assertFalse(token.isEmpty(), "Token不应为空");
    }

    @Test
    @DisplayName("测试用户注册失败 - 用户名已存在")
    void testRegisterFailureUsernameExists() {
        // 先注册一个用户
        String username = "existing_user";
        authService.register(username, "existing@example.com", "password123");

        // 尝试用相同的用户名再次注册
        ApiException exception = assertThrows(ApiException.class, () -> {
            authService.register(username, "another_email@example.com", "password123");
        });

        // 验证异常信息是否符合预期
        assertEquals("用户名重复", exception.getMessage());
    }

    @Test
    @DisplayName("测试用户登录失败 - 用户不存在")
    void testLoginFailureUserNotExists() {
        // 尝试用一个不存在的用户名登录
        ApiException exception = assertThrows(ApiException.class, () -> {
            authService.login("non_existent_user", "password123");
        });

        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    @DisplayName("测试用户登录失败 - 密码错误")
    void testLoginFailureWrongPassword() {
        // 注册一个用户
        String username = "user_with_wrong_pass";
        String correctPassword = "password123";
        String wrongPassword = "wrong_password";
        authService.register(username, "wrong_pass@example.com", correctPassword);

        // 尝试用错误的密码登录
        ApiException exception = assertThrows(ApiException.class, () -> {
            authService.login(username, wrongPassword);
        });

        assertEquals("用户名或密码错误", exception.getMessage());
    }
} 