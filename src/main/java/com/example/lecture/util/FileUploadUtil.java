package com.example.lecture.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * 文件上传工具类
 */
public class FileUploadUtil {
    
    /**
     * 文件允许的类型
     */
    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif"};
    
    /**
     * 检查文件是否为允许的类型
     * 
     * @param fileName 文件名
     * @return 是否为允许的类型
     */
    public static boolean isAllowedExtension(String fileName) {
        String extension = FileUtil.extName(fileName);
        return Arrays.stream(ALLOWED_EXTENSIONS)
                .anyMatch(ext -> ext.equalsIgnoreCase("." + extension));
    }
    
    /**
     * 生成唯一文件名
     * 
     * @param originalFilename 原始文件名
     * @return 唯一文件名
     */
    public static String generateUniqueFileName(String originalFilename) {
        String extension = FileUtil.extName(originalFilename);
        return IdUtil.fastSimpleUUID() + "." + extension;
    }
    
    /**
     * 保存文件
     * 
     * @param file 文件
     * @param targetDir 目标目录
     * @return 保存后的文件名，失败返回null
     */
    public static String saveFile(MultipartFile file, String targetDir) {
        if (file.isEmpty()) {
            return null;
        }
        
        String originalFilename = file.getOriginalFilename();
        if (!isAllowedExtension(originalFilename)) {
            return null;
        }
        
        // 生成唯一文件名
        String fileName = generateUniqueFileName(originalFilename);
        
        // 创建目标目录
        File dir = new File(targetDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // 保存文件
        try {
            File destFile = new File(dir, fileName);
            file.transferTo(destFile);
            return fileName;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
} 