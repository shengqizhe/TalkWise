package com.example.lecture.service;

import com.example.lecture.dto.AiAssistantRequestDTO;
import com.example.lecture.dto.AiAssistantResponseDTO;

/**
 * AI助手服务接口
 */
public interface AiAssistantService {
    
    /**
     * AI对话
     */
    AiAssistantResponseDTO chat(AiAssistantRequestDTO request);
    
    /**
     * 获取用户已报名的讲座
     */
    AiAssistantResponseDTO getUserRegistrations(Long userId);
    
    /**
     * 取消用户报名
     */
    AiAssistantResponseDTO cancelUserRegistration(Long userId, Long lectureId);
}