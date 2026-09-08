package com.example.lecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("简单测试")
class SimpleTest {

    @Test
    @DisplayName("测试基本断言")
    void testBasicAssertion() {
        assertTrue(true, "基本断言测试");
        assertEquals(2, 1 + 1, "基本计算测试");
        assertNotNull("Hello", "字符串非空测试");
    }

    @Test
    @DisplayName("测试字符串操作")
    void testStringOperations() {
        String str = "Hello World";
        assertTrue(str.contains("Hello"));
        assertEquals(11, str.length());
        assertFalse(str.isEmpty());
    }

    @Test
    @DisplayName("测试数组操作")
    void testArrayOperations() {
        int[] numbers = {1, 2, 3, 4, 5};
        assertEquals(5, numbers.length);
        assertEquals(1, numbers[0]);
        assertEquals(5, numbers[4]);
    }
} 