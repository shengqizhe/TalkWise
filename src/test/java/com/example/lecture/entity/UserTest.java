package com.example.lecture.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@DisplayName("用户实体类测试")
class UserTest {

    @Test
    @DisplayName("测试用户实体基本属性设置和获取")
    void testUserBasicProperties() {
        User user = new User();
        
        // 设置基本属性
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("password123");
        user.setRealName("张三");
        user.setStudentTeacherId("2021001");
        user.setEmail("test@example.com");
        user.setPhone("13800138000");
        user.setAvatar("avatar.jpg");
        user.setInterestTags("[\"技术\",\"科学\"]");
        user.setParticipationScore(new BigDecimal("85.5"));
        user.setDeleted(0);
        
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        
        // 验证属性值
        assertEquals(1L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("password123", user.getPassword());
        assertEquals("张三", user.getRealName());
        assertEquals("2021001", user.getStudentTeacherId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("13800138000", user.getPhone());
        assertEquals("avatar.jpg", user.getAvatar());
        assertEquals("[\"技术\",\"科学\"]", user.getInterestTags());
        assertEquals(new BigDecimal("85.5"), user.getParticipationScore());
        assertEquals(0, user.getDeleted());
        assertEquals(now, user.getCreatedTime());
        assertEquals(now, user.getUpdatedTime());
    }

    @Test
    @DisplayName("测试用户实体默认值")
    void testUserDefaultValues() {
        User user = new User();
        
        // 验证默认值
        assertNull(user.getId());
        assertNull(user.getUsername());
        assertNull(user.getPassword());
        assertNull(user.getRealName());
        assertNull(user.getStudentTeacherId());
        assertNull(user.getEmail());
        assertNull(user.getPhone());
        assertNull(user.getAvatar());
        assertNull(user.getInterestTags());
        assertNull(user.getParticipationScore());
        assertNull(user.getDeleted());
        assertNull(user.getCreatedTime());
        assertNull(user.getUpdatedTime());
    }

    @Test
    @DisplayName("测试用户实体边界值")
    void testUserBoundaryValues() {
        User user = new User();
        
        // 测试边界值
        user.setId(Long.MAX_VALUE);
        user.setParticipationScore(new BigDecimal("100.00"));
        user.setParticipationScore(new BigDecimal("0.00"));
        
        assertEquals(Long.MAX_VALUE, user.getId());
        assertEquals(new BigDecimal("0.00"), user.getParticipationScore());
    }
} 