package com.example.lecture;

import com.example.university_lecture_management_system.UniversityLectureManagementSystemApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = UniversityLectureManagementSystemApplication.class)
@ActiveProfiles("test")
@DisplayName("基础功能测试")
class BasicTest {

    @Test
    @DisplayName("测试Spring上下文加载")
    void testContextLoads() {
        assertTrue(true, "Spring上下文加载成功");
    }

    @Test
    @DisplayName("测试基本计算")
    void testBasicCalculation() {
        int result = 2 + 2;
        assertEquals(4, result, "基本计算测试");
    }

    @Test
    @DisplayName("测试字符串操作")
    void testStringOperation() {
        String str = "Hello World";
        assertTrue(str.contains("Hello"), "字符串包含测试");
        assertEquals(11, str.length(), "字符串长度测试");
    }


} 