package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.dto.EvaluationDTO;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.User;
import com.example.lecture.mapper.EvaluationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.UserMapper;
import com.example.lecture.service.EvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评价Service实现类
 */
@Service
public class EvaluationServiceImpl extends ServiceImpl<EvaluationMapper, Evaluation> implements EvaluationService {

    @Autowired
    private LectureMapper lectureMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Page<EvaluationDTO> getEvaluationPage(Page<Evaluation> page, String keyword, Long lectureId, Long teacherId, String sort, String order)  {
        // 构建查询条件
        LambdaQueryWrapper<Evaluation> queryWrapper = new LambdaQueryWrapper<>();

        if (lectureId != null) {
            queryWrapper.eq(Evaluation::getLectureId, lectureId);
        }

        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(Evaluation::getContent, keyword);
        }
        
        // 如果指定了教师ID，需要先查询该教师的讲座ID列表
        if (teacherId != null) {
            // 查询该教师的所有讲座ID
            LambdaQueryWrapper<Lecture> lectureWrapper = new LambdaQueryWrapper<>();
            lectureWrapper.eq(Lecture::getOrganizerId, teacherId)
                         .eq(Lecture::getDeleted, 0)
                         .select(Lecture::getId);
            List<Lecture> teacherLectures = lectureMapper.selectList(lectureWrapper);
            
            if (teacherLectures.isEmpty()) {
                // 如果教师没有讲座，返回空结果
                Page<EvaluationDTO> emptyPage = new Page<>();
                emptyPage.setRecords(new ArrayList<>());
                emptyPage.setTotal(0L);
                emptyPage.setCurrent(page.getCurrent());
                emptyPage.setSize(page.getSize());
                return emptyPage;
            }
            
            // 获取讲座ID列表
            List<Long> lectureIds = teacherLectures.stream()
                    .map(Lecture::getId)
                    .collect(Collectors.toList());
            
            // 添加讲座ID条件
            queryWrapper.in(Evaluation::getLectureId, lectureIds);
        }
        
        // 动态排序
        if (StringUtils.hasText(sort)) {
            boolean isAsc = "asc".equalsIgnoreCase(order);
            switch (sort) {
                case "score":
                    if (isAsc) {
                        queryWrapper.orderByAsc(Evaluation::getScore);
                    } else {
                        queryWrapper.orderByDesc(Evaluation::getScore);
                    }
                    break;
                default:
                    queryWrapper.orderByDesc(Evaluation::getCreatedTime);
            }
        } else {
            queryWrapper.orderByDesc(Evaluation::getCreatedTime);
        }

        // 分页查询评价
        Page<Evaluation> evaluationPage = this.page(page, queryWrapper);

        // 转换为DTO
        List<EvaluationDTO> evaluationDTOs = evaluationPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // 构建返回结果
        Page<EvaluationDTO> resultPage = new Page<>();
        resultPage.setRecords(evaluationDTOs);
        resultPage.setTotal(evaluationPage.getTotal());
        resultPage.setCurrent(evaluationPage.getCurrent());
        resultPage.setSize(evaluationPage.getSize());

        return resultPage;
    }


    @Override
    public List<EvaluationDTO> getEvaluationsByLectureId(Long lectureId) {
        LambdaQueryWrapper<Evaluation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Evaluation::getLectureId, lectureId)
                .orderByDesc(Evaluation::getCreatedTime);

        return this.list(queryWrapper).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvaluationDTO> getEvaluationsByUserId(Long userId) {
        LambdaQueryWrapper<Evaluation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Evaluation::getUserId, userId)
                .orderByDesc(Evaluation::getCreatedTime);

        return this.list(queryWrapper).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Double getAverageScoreByLectureId(Long lectureId) {
        LambdaQueryWrapper<Evaluation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Evaluation::getLectureId, lectureId)
                .select(Evaluation::getScore);

        List<Evaluation> evaluations = this.list(queryWrapper);

        if (evaluations.isEmpty()) {
            return 0.0;
        }

        BigDecimal totalScore = evaluations.stream()
                .map(Evaluation::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalScore.divide(BigDecimal.valueOf(evaluations.size()), 2, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
    }

    @Override
    public EvaluationStats getEvaluationStatsByLectureId(Long lectureId) {
        LambdaQueryWrapper<Evaluation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Evaluation::getLectureId, lectureId);

        List<Evaluation> evaluations = this.list(queryWrapper);

        EvaluationStats stats = new EvaluationStats();
        stats.setTotalCount(evaluations.size());

        if (evaluations.isEmpty()) {
            stats.setAverageScore(0.0);
            return stats;
        }

        // 计算平均分
        BigDecimal totalScore = evaluations.stream()
                .map(Evaluation::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setAverageScore(totalScore.divide(BigDecimal.valueOf(evaluations.size()), 2, BigDecimal.ROUND_HALF_UP)
                .doubleValue());

        return stats;
    }

    /**
     * 转换为DTO
     */
    private EvaluationDTO convertToDTO(Evaluation evaluation) {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setId(evaluation.getId());
        dto.setUserId(evaluation.getUserId());
        dto.setLectureId(evaluation.getLectureId());
        dto.setScore(evaluation.getScore());
        dto.setContent(evaluation.getContent());
        dto.setSentimentScore(evaluation.getSentimentScore());
        dto.setImprovementSuggestion(evaluation.getImprovementSuggestion());
        dto.setCreatedTime(evaluation.getCreatedTime());
        dto.setUpdatedTime(evaluation.getUpdatedTime());

        // 获取用户信息
        User user = userMapper.selectById(evaluation.getUserId());
        if (user != null) {
            dto.setUserName(user.getRealName() != null ? user.getRealName() : user.getUsername());
        }

        // 获取讲座信息
        Lecture lecture = lectureMapper.selectById(evaluation.getLectureId());
        if (lecture != null) {
            dto.setLectureTitle(lecture.getTitle());
        }

        return dto;
    }
} 