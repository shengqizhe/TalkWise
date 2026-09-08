package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.dto.EvaluationDTO;

import java.util.List;

/**
 * 评价Service接口
 */
public interface EvaluationService extends IService<Evaluation> {
    
    /**
     * 分页查询评价列表
     */
    Page<EvaluationDTO> getEvaluationPage(Page<Evaluation> page, String keyword, Long lectureId, Long teacherId, String sort, String order);
    
    /**
     * 获取讲座的评价列表
     */
    List<EvaluationDTO> getEvaluationsByLectureId(Long lectureId);
    
    /**
     * 获取用户的评价列表
     */
    List<EvaluationDTO> getEvaluationsByUserId(Long userId);
    
    /**
     * 获取讲座的平均评分
     */
    Double getAverageScoreByLectureId(Long lectureId);
    
    /**
     * 获取讲座的评价统计
     */
    EvaluationStats getEvaluationStatsByLectureId(Long lectureId);
    /**
     * 评价统计信息
     */
    class EvaluationStats {
        private Integer totalCount;
        private Double averageScore;
        private Integer fiveStarCount;
        private Integer fourStarCount;
        private Integer threeStarCount;
        private Integer twoStarCount;
        private Integer oneStarCount;
        
        // getters and setters
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        
        public Double getAverageScore() { return averageScore; }
        public void setAverageScore(Double averageScore) { this.averageScore = averageScore; }
        
        public Integer getFiveStarCount() { return fiveStarCount; }
        public void setFiveStarCount(Integer fiveStarCount) { this.fiveStarCount = fiveStarCount; }
        
        public Integer getFourStarCount() { return fourStarCount; }
        public void setFourStarCount(Integer fourStarCount) { this.fourStarCount = fourStarCount; }
        
        public Integer getThreeStarCount() { return threeStarCount; }
        public void setThreeStarCount(Integer threeStarCount) { this.threeStarCount = threeStarCount; }
        
        public Integer getTwoStarCount() { return twoStarCount; }
        public void setTwoStarCount(Integer twoStarCount) { this.twoStarCount = twoStarCount; }
        
        public Integer getOneStarCount() { return oneStarCount; }
        public void setOneStarCount(Integer oneStarCount) { this.oneStarCount = oneStarCount; }
    }
} 