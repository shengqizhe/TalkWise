package com.example.lecture.security;

import com.example.lecture.dto.LoginDTO;
import com.example.lecture.dto.RegisterDTO;
import com.example.university_lecture_management_system.UniversityLectureManagementSystemApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = UniversityLectureManagementSystemApplication.class)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DisplayName("安全测试")
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试SQL注入防护")
    void testSqlInjectionProtection() throws Exception {
        // 测试SQL注入攻击
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("'; DROP TABLE user; --");
        loginDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("测试XSS防护")
    void testXssProtection() throws Exception {
        // 测试XSS攻击
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("<script>alert('xss')</script>");
        registerDTO.setEmail("xss@example.com");
        registerDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("测试密码强度验证")
    void testPasswordStrengthValidation() throws Exception {
        // 测试弱密码
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("weakpassuser");
        registerDTO.setEmail("weak@example.com");
        registerDTO.setPassword("123"); // 弱密码

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("测试邮箱格式验证")
    void testEmailFormatValidation() throws Exception {
        // 测试无效邮箱格式
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("invalidemailuser");
        registerDTO.setEmail("invalid-email-format");
        registerDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("测试输入长度限制")
    void testInputLengthValidation() throws Exception {
        // 测试超长输入
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("a".repeat(100)); // 超长用户名
        registerDTO.setEmail("long@example.com");
        registerDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isBadRequest());
    }
} 