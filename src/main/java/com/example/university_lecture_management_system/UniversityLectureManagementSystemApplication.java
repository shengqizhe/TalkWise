package com.example.university_lecture_management_system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 大学讲座管理系统启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.lecture", "com.example.university_lecture_management_system"})
@MapperScan("com.example.lecture.mapper")
@EnableScheduling
public class UniversityLectureManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityLectureManagementSystemApplication.class, args);
    }

}