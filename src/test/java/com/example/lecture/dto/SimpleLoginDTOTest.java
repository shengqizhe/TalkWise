package com.example.lecture.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("简单登录DTO测试")
class SimpleLoginDTOTest {

    @Test
    @DisplayName("测试LoginDTO对象创建")
    void testLoginDTOCreation() {
        LoginDTO loginDTO = new LoginDTO();
        assertNotNull(loginDTO, "LoginDTO对象应该能够创建");
    }

    @Test
    @DisplayName("测试LoginDTO属性设置")
    void testLoginDTOProperties() {
        LoginDTO loginDTO = new LoginDTO();
        
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password123");
        
        assertEquals("testuser", loginDTO.getUsername());
        assertEquals("password123", loginDTO.getPassword());
    }

    @Test
    @DisplayName("测试LoginDTO默认值")
    void testLoginDTODefaultValues() {
        LoginDTO loginDTO = new LoginDTO();
        
        assertNull(loginDTO.getUsername());
        assertNull(loginDTO.getPassword());
    }
} 