package com.example.lecture.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

@DisplayName("登录DTO测试")
class LoginDTOTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    @DisplayName("测试有效的登录DTO")
    void testValidLoginDTO() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password123");
        
        Set<ConstraintViolation<LoginDTO>> violations = validator.validate(loginDTO);
        assertTrue(violations.isEmpty(), "有效的登录DTO不应该有验证错误");
        
        assertEquals("testuser", loginDTO.getUsername());
        assertEquals("password123", loginDTO.getPassword());
    }

    @Test
    @DisplayName("测试无效的用户名")
    void testInvalidUsername() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(""); // 空用户名
        loginDTO.setPassword("password123");
        
        Set<ConstraintViolation<LoginDTO>> violations = validator.validate(loginDTO);
        assertFalse(violations.isEmpty(), "空用户名应该产生验证错误");
    }

    @Test
    @DisplayName("测试无效的密码")
    void testInvalidPassword() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword(""); // 空密码
        
        Set<ConstraintViolation<LoginDTO>> violations = validator.validate(loginDTO);
        assertFalse(violations.isEmpty(), "空密码应该产生验证错误");
    }

    @Test
    @DisplayName("测试空用户名")
    void testNullUsername() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(null); // null用户名
        loginDTO.setPassword("password123");
        
        Set<ConstraintViolation<LoginDTO>> violations = validator.validate(loginDTO);
        assertFalse(violations.isEmpty(), "null用户名应该产生验证错误");
    }

    @Test
    @DisplayName("测试空密码")
    void testNullPassword() {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword(null); // null密码
        
        Set<ConstraintViolation<LoginDTO>> violations = validator.validate(loginDTO);
        assertFalse(violations.isEmpty(), "null密码应该产生验证错误");
    }
} 