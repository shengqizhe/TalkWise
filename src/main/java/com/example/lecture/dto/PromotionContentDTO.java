package com.example.lecture.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 讲座宣传图片DTO
 */
@Data
@Schema(description = "只上传宣讲图片")
public class PromotionContentDTO {
    
    /**
     * 宣传图片列表
     */
    @Schema(description = "宣传图片列表")
    private List<PromotionImage> images;
    
    /**
     * 宣传图片信息
     */
    @Data
    @Schema(description = "宣传图片信息")
    public static class PromotionImage {
        
        /**
         * 图片URL
         */
        @Schema(description = "图片URL")
        private String url;
        
        /**
         * 图片标题
         */
        @Schema(description = "图片标题")
        private String title;
        
        /**
         * 图片描述
         */
        @Schema(description = "图片描述")
        private String description;
        
        /**
         * 图片类型（banner/thumbnail/detail）
         */
        @Schema(description = "图片类型")
        private String type;
    }
    

}