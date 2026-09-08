package com.example.lecture.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

@DisplayName("注册DTO测试")
class RegisterDTOTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    @DisplayName("测试有效的注册DTO")
    void testValidRegisterDTO() {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("newuser");
        registerDTO.setEmail("newuser@example.com");
        registerDTO.setPassword("password123");
        
        Set<ConstraintViolation<RegisterDTO>> violations = validator.validate(registerDTO);
        assertTrue(violations.isEmpty(), "有效的注册DTO不应该有验证错误");
        
        assertEquals("newuser", registerDTO.getUsername());
        assertEquals("newuser@example.com", registerDTO.getEmail());
        assertEquals("password123", registerDTO.getPassword());
    }

    @Test
    @DisplayName("测试无效的邮箱格式")
    void testInvalidEmailFormat() {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("newuser");
        registerDTO.setEmail("invalid-email-format");
        registerDTO.setPassword("password123");
        
        Set<ConstraintViolation<RegisterDTO>> violations = validator.validate(registerDTO);
        assertFalse(violations.isEmpty(), "无效的邮箱格式应该产生验证错误");
    }

    @Test
    @DisplayName("测试密码强度")
    void testPasswordStrength() {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("newuser");
        registerDTO.setEmail("newuser@example.com");
        registerDTO.setPassword("123"); // 弱密码
        
        Set<ConstraintViolation<RegisterDTO>> violations = validator.validate(registerDTO);
        assertFalse(violations.isEmpty(), "弱密码应该产生验证错误");
    }
} 