package com.example.lecture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * 文件上传配置类
 */
@Configuration
public class FileUploadConfig {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.avatar}")
    private String avatarPath;

    /**
     * 项目启动时创建上传目录
     */
    @PostConstruct
    public void init() {
        // 创建主上传目录
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // 创建头像上传目录
        File avatarDir = new File(avatarPath);
        if (!avatarDir.exists()) {
            avatarDir.mkdirs();
        }
    }
} 