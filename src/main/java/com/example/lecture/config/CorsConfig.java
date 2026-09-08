package com.example.lecture.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置类
 * 解决前端Vue3应用与后端Spring Boot应用之间的跨域问题
 */
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许所有域名进行跨域调用（开发环境）
        config.addAllowedOriginPattern("*");
        
        // 允许跨域发送cookie（需要配合前端withCredentials: true使用）
        config.setAllowCredentials(true);
        
        // 放行全部原始头信息
        config.addAllowedHeader("*");
        
        // 允许所有请求方法跨域调用（GET, POST, PUT, DELETE, OPTIONS等）
        config.addAllowedMethod("*");
        
        // 暴露头部信息，让前端可以访问Authorization头
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Type");
        config.addExposedHeader("Accept");
        
        // 预检请求的缓存时间（秒），即在这个时间段里，对于相同的跨域请求不会再预检了
        config.setMaxAge(3600L);
        
        // 创建URL基于的CORS配置源
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径应用CORS配置
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
} 