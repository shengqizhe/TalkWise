package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.dto.RegistrationLectureDTO;
import com.example.lecture.dto.RegistrationUserDTO;
import com.example.lecture.entity.Registration;

import java.util.List;

/**
 * 报名记录Service接口
 */
public interface RegistrationService extends IService<Registration> {
    
    /**
     * 报名讲座
     */
    void register(Long userId, Long lectureId);
    
    /**
     * 取消报名
     */
    void cancel(Long userId, Long lectureId);
    
    /**
     * 检查是否已报名
     */
    boolean isRegistered(Long userId, Long lectureId);
    
    /**
     * 获取讲座报名列表
     */
    List<RegistrationUserDTO> getLectureRegistrations(Long lectureId);
    
    /**
     * 获取用户报名列表
     * @param userId 用户ID
     * @param status 状态(可选): 1-已报名, 2-已取消
     * @return 报名讲座列表
     */
    List<RegistrationLectureDTO> getUserRegistrations(Long userId, Integer status);
    
    /**
     * 学生签到
     * @param registrationId 报名记录ID
     */
    void checkin(Long registrationId);
    
    /**
     * 取消签到
     * @param registrationId 报名记录ID
     */
    void cancelCheckin(Long registrationId);
    
    /**
     * 重置指定讲座的所有报名签到状态
     * @param lectureId 讲座ID
     */
    void resetCheckinStatusByLectureId(Long lectureId);
}