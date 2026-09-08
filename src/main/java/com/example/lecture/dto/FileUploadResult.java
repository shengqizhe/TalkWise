package com.example.lecture.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResult {
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件URL
     */
    private String url;
} 