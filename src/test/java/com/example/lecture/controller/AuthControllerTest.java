package com.example.lecture.controller;

import com.example.lecture.dto.LoginDTO;
import com.example.lecture.dto.RegisterDTO;
import com.example.lecture.dto.ResetPasswordDTO;
import com.example.lecture.service.AuthService;
import com.example.university_lecture_management_system.UniversityLectureManagementSystemApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UniversityLectureManagementSystemApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("认证控制器测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(authService.login(anyString(), anyString())).thenReturn("test-token-123");
        doNothing().when(authService).register(anyString(), anyString(), anyString());
        doNothing().when(authService).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("测试用户登录成功")
    void testLoginSuccess() throws Exception {
        // 准备测试数据
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password123");

        String expectedToken = "test-token-123";

        // 执行测试
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.token").value(expectedToken));

        // 验证 authService 的 login 方法确实被以正确的参数调用了
        verify(authService).login("testuser", "password123");
    }

    @Test
    @DisplayName("测试用户注册成功")
    void testRegisterSuccess() throws Exception {
        // 准备测试数据
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("newuser");
        registerDTO.setEmail("newuser@example.com");
        registerDTO.setPassword("password123");

        // 执行测试
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        // 验证 authService 的 register 方法确实被以正确的参数调用了
        verify(authService).register("newuser", "newuser@example.com", "password123");
    }

    @Test
    @DisplayName("测试重置密码成功")
    void testResetPasswordSuccess() throws Exception {
        // 准备测试数据
        ResetPasswordDTO resetPasswordDTO = new ResetPasswordDTO();
        resetPasswordDTO.setEmail("user@example.com");
        resetPasswordDTO.setNewPassword("newpassword123");

        // 执行测试
        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetPasswordDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        // 验证 authService 的 resetPassword 方法确实被以正确的参数调用了
        verify(authService).resetPassword("user@example.com", "newpassword123");
    }
} 