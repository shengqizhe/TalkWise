package com.example.lecture.util;

import com.example.lecture.dto.PromotionContentDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 宣讲图片工具类
 * 用于处理讲座宣讲图片的验证、转换和清理
 */
@Slf4j
public class PromotionContentUtil {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 将 PromotionContentDTO 转换为 Map
     * @param dto PromotionContentDTO 对象
     * @return Map<String, Object>
     */
    public static Map<String, Object> dtoToMap(PromotionContentDTO dto) {
        if (dto == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(dto, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("PromotionContentDTO 转换为 Map 失败", e);
            throw new RuntimeException("宣讲图片格式转换失败: " + e.getMessage());
        }
    }
    
    /**
     * 将 Map 转换为 PromotionContentDTO
     * @param map Map<String, Object> 对象
     * @return PromotionContentDTO
     */
    public static PromotionContentDTO mapToDto(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new PromotionContentDTO();
        }
        try {
            return objectMapper.convertValue(map, PromotionContentDTO.class);
        } catch (Exception e) {
            log.error("Map 转换为 PromotionContentDTO 失败", e);
            throw new RuntimeException("宣讲图片格式解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证宣传图片的基本格式
     * @param dto PromotionContentDTO 对象
     * @return 验证结果
     */
    public static boolean validatePromotionContent(PromotionContentDTO dto) {
        if (dto == null) {
            return false;
        }
        
        // 验证图片数量
        if (dto.getImages() != null && dto.getImages().size() > 10) {
            throw new RuntimeException("宣传图片数量不能超过10张");
        }
        
        // 验证图片URL格式
        if (dto.getImages() != null) {
            for (PromotionContentDTO.PromotionImage image : dto.getImages()) {
                if (image != null && StringUtils.hasText(image.getUrl())) {
                    if (!isValidUrl(image.getUrl())) {
                        throw new RuntimeException("图片URL格式不正确: " + image.getUrl());
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * 简单的URL格式验证
     * @param url URL字符串
     * @return 是否为有效URL
     */
    private static boolean isValidUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/");
    }
    
    /**
     * 清理和标准化宣传图片内容
     * @param dto PromotionContentDTO 对象
     * @return 清理后的 PromotionContentDTO
     */
    public static PromotionContentDTO sanitizePromotionContent(PromotionContentDTO dto) {
        if (dto == null) {
            return new PromotionContentDTO();
        }
        
        // 清理图片信息中的多余空格
        if (dto.getImages() != null) {
            for (PromotionContentDTO.PromotionImage image : dto.getImages()) {
                if (image != null) {
                    if (StringUtils.hasText(image.getTitle())) {
                        image.setTitle(image.getTitle().trim());
                    }
                    if (StringUtils.hasText(image.getDescription())) {
                        image.setDescription(image.getDescription().trim());
                    }
                    if (StringUtils.hasText(image.getType())) {
                        image.setType(image.getType().trim());
                    }
                }
            }
        }
        
        return dto;
    }
}