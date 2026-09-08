package com.example.lecture.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("简单用户实体测试")
class SimpleUserTest {

    @Test
    @DisplayName("测试用户对象创建")
    void testUserCreation() {
        User user = new User();
        assertNotNull(user, "用户对象应该能够创建");
    }

    @Test
    @DisplayName("测试用户基本属性设置")
    void testUserBasicProperties() {
        User user = new User();
        
        // 设置基本属性
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("password123");
        user.setRealName("张三");
        
        // 验证属性值
        assertEquals(1L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("password123", user.getPassword());
        assertEquals("张三", user.getRealName());
    }

    @Test
    @DisplayName("测试用户默认值")
    void testUserDefaultValues() {
        User user = new User();
        
        // 验证默认值
        assertNull(user.getId());
        assertNull(user.getUsername());
        assertNull(user.getPassword());
        assertNull(user.getRealName());
    }
} 