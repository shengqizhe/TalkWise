package com.example.lecture.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.lecture.common.api.Result;
import com.example.lecture.dto.FileUploadResult;
import com.example.lecture.util.FileUploadUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 */
@Tag(name = "文件上传", description = "文件上传相关接口")
@RestController
@RequestMapping("/file")
public class FileUploadController {

    @Value("${file.upload.avatar}")
    private String avatarPath;
    
    @Value("${file.upload.lecture}")
    private String lecturePath;
    
    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 上传头像
     * 
     * @param file 文件
     * @return 上传结果
     */
    @Operation(summary = "上传头像")
    @PostMapping("/upload/avatar")
    @SaCheckLogin
    public Result<FileUploadResult> uploadAvatar(@RequestParam("file") MultipartFile file) {
        // 检查文件是否为空
        if (file.isEmpty()) {
            return Result.failed("请选择文件");
        }
        
        // 检查文件类型
        String originalFilename = file.getOriginalFilename();
        if (!FileUploadUtil.isAllowedExtension(originalFilename)) {
            return Result.failed("只支持jpg、jpeg、png、gif格式的图片");
        }
        
        // 保存文件
        String fileName = FileUploadUtil.saveFile(file, avatarPath);
        if (fileName == null) {
            return Result.failed("文件上传失败");
        }
        
        // 构建URL
        String url = contextPath + "/uploads/avatar/" + fileName;
        
        // 返回结果
        FileUploadResult result = new FileUploadResult(fileName, url);
        return Result.success(result);
    }
    
    /**
     * 上传宣讲图片
     * 
     * @param file 文件
     * @return 上传结果
     */
    @Operation(summary = "上传宣讲图片")
    @PostMapping("/upload/lecture")
    @SaCheckLogin
    public Result<FileUploadResult> uploadLectureImage(@RequestParam("file") MultipartFile file) {
        // 检查文件是否为空
        if (file.isEmpty()) {
            return Result.failed("请选择文件");
        }
        
        // 检查文件类型
        String originalFilename = file.getOriginalFilename();
        if (!FileUploadUtil.isAllowedExtension(originalFilename)) {
            return Result.failed("只支持jpg、jpeg、png、gif格式的图片");
        }
        
        // 保存文件
        String fileName = FileUploadUtil.saveFile(file, lecturePath);
        if (fileName == null) {
            return Result.failed("文件上传失败");
        }
        
        // 构建URL
        String url = contextPath + "/uploads/lecture/" + fileName;
        
        // 返回结果
        FileUploadResult result = new FileUploadResult(fileName, url);
        return Result.success(result);
    }
}