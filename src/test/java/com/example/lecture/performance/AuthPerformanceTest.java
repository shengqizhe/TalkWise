package com.example.lecture.performance;

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = UniversityLectureManagementSystemApplication.class)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DisplayName("认证功能性能测试")
class AuthPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试并发注册性能")
    void testConcurrentRegistrationPerformance() throws Exception {
        int threadCount = 10;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        RegisterDTO registerDTO = new RegisterDTO();
                        registerDTO.setUsername("perfuser" + threadId + "_" + j);
                        registerDTO.setEmail("perf" + threadId + "_" + j + "@example.com");
                        registerDTO.setPassword("password123");

                        mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerDTO)))
                                .andExpect(status().isOk());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        long totalTime = endTime - startTime;
        int totalRequests = threadCount * requestsPerThread;
        double requestsPerSecond = (double) totalRequests / (totalTime / 1000.0);

        System.out.println("并发注册测试结果:");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("总时间: " + totalTime + "ms");
        System.out.println("每秒请求数: " + String.format("%.2f", requestsPerSecond));

        // 性能断言
        assertTrue(requestsPerSecond > 10, "每秒请求数应该大于10");
        assertTrue(totalTime < 30000, "总执行时间应该小于30秒");

        executor.shutdown();
    }

    @Test
    @DisplayName("测试单次请求响应时间")
    void testSingleRequestResponseTime() throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("singletestuser");
        registerDTO.setEmail("single@example.com");
        registerDTO.setPassword("password123");

        long startTime = System.currentTimeMillis();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isOk());

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        System.out.println("单次注册请求响应时间: " + responseTime + "ms");

        // 响应时间断言
        assertTrue(responseTime < 1000, "单次请求响应时间应该小于1秒");
    }
} 