package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.entity.Lecture;

import java.util.List;
import java.util.Map;
import com.example.lecture.dto.PromotionContentDTO;

/**
 * 讲座Service接口
 */
public interface LectureService extends IService<Lecture> {
    
    /**
     * 分页查询讲座列表
     */
    Page<Lecture> page(Page<Lecture> page, String keyword, Long categoryId, Long organizerId, Integer publishStatus);
    
    /**
     * 分页查询讲座列表（支持按状态筛选）
     */
    Page<Lecture> page(Page<Lecture> page, String keyword, Long categoryId, Long organizerId, Integer publishStatus, Integer status);
    
    /**
     * 发布讲座
     */
    void publish(Lecture lecture);
    
    /**
     * 更新讲座
     */
    void update(Lecture lecture);
    
    /**
     * 取消讲座
     */
    void cancel(Long id, String reason);
    
    /**
     * 更新讲座状态
     */
    void updateStatus(Long id, Integer status);
    
    /**
     * 更新讲座发布状态
     */
    void updatePublishStatus(Long id, Integer publishStatus);
    
    /**
     * 更新报名人数
     */
    void updateRegisteredCount(Long id, int count);

    Lecture getById(Long id);

    /**
     * 检查数据是否自上次时间戳以来有变化
     *
     * @param lectures             当前讲座列表
     * @param lastUpdatedTimestamp 上次更新时间戳
     * @return 如果数据有变化返回true，否则返回false
     */
    boolean hasDataChangedSince(List<Lecture> lectures, Long lastUpdatedTimestamp);
    
    /**
     * 更新讲座宣传内容
     * @param lectureId 讲座ID
     * @param promotionContent 宣传内容
     */
    void updatePromotionContent(Long lectureId, PromotionContentDTO promotionContent);
    
    /**
     * 获取讲座宣传内容
     * @param lectureId 讲座ID
     * @return 宣传内容
     */
    PromotionContentDTO getPromotionContent(Long lectureId);
    
    /**
     * 删除讲座宣传内容
     *
     * @param lectureId 讲座ID
     */
    void deletePromotionContent(Long lectureId);
}