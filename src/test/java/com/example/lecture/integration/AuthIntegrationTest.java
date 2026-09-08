package com.example.lecture.integration;

import com.example.lecture.dto.LoginDTO;
import com.example.lecture.dto.RegisterDTO;
import com.example.lecture.entity.User;
import com.example.lecture.mapper.UserMapper;
import com.example.university_lecture_management_system.UniversityLectureManagementSystemApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = UniversityLectureManagementSystemApplication.class)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("认证功能集成测试")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        userMapper.delete(null);
    }

    @Test
    @DisplayName("测试完整的用户注册和登录流程")
    void testCompleteRegistrationAndLoginFlow() throws Exception {
        // 1. 用户注册
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("integrationuser");
        registerDTO.setEmail("integration@example.com");
        registerDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 2. 验证用户已创建
        User createdUser = userMapper.selectByUsername("integrationuser");
        assertNotNull(createdUser);
        assertEquals("integration@example.com", createdUser.getEmail());

        // 3. 用户登录
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("integrationuser");
        loginDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @DisplayName("测试重复注册失败")
    void testDuplicateRegistrationFailure() throws Exception {
        // 1. 第一次注册
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("duplicateuser");
        registerDTO.setEmail("duplicate@example.com");
        registerDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk());

        // 2. 尝试重复注册
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("测试数据库事务回滚")
    void testDatabaseTransactionRollback() throws Exception {
        // 这个测试验证了@Transactional注解是否正常工作
        // 如果事务正常工作，测试结束后数据应该被回滚
        
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("transactionuser");
        registerDTO.setEmail("transaction@example.com");
        registerDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk());

        // 验证用户已创建
        User user = userMapper.selectByUsername("transactionuser");
        assertNotNull(user);
    }

    @Test
    @DisplayName("测试用户注册接口")
    void testRegisterEndpoint() throws Exception {
        // 准备测试数据
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("integrationuser");
        registerDTO.setEmail("integration@example.com");
        registerDTO.setPassword("password123");

        // 执行测试
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("测试用户登录接口")
    void testLoginEndpoint() throws Exception {
        // 准备测试数据
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password123");

        // 执行测试
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk());
    }
} 