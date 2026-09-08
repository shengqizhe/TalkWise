package com.example.lecture;

import com.example.lecture.controller.EmailController;
import com.example.university_lecture_management_system.UniversityLectureManagementSystemApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = UniversityLectureManagementSystemApplication.class)
class EmailControllerTest {

    @Autowired
    private EmailController emailController;

    @Test
    void sendMail() {
        emailController.sendMail(
                "3775262845@qq.com",
                "1719890482@qq.com",
                "3775262845@qq.com",
                "你是什么？",
                "是!"
        );
    }
}