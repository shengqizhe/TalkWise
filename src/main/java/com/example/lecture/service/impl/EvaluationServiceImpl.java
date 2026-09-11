package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.dev33.satoken.stp.StpUtil;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.dto.EvaluationDTO;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Registration;
import com.example.lecture.entity.User;
import com.example.lecture.mapper.EvaluationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.RegistrationMapper;
import com.example.lecture.mapper.UserMapper;
import com.example.lecture.service.EvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private RegistrationMapper registrationMapper;

    // ==================== 写操作（含业务校验与归属校验） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createEvaluation(Evaluation evaluation) {
        if (evaluation.getLectureId() == null) {
            throw new ApiException("讲座ID不能为空");
        }
        validateScore(evaluation.getScore());

        // userId 以登录态为准，忽略请求体传入值（防伪造）
        Long userId = currentUserId();
        evaluation.setUserId(userId);

        Lecture lecture = lectureMapper.selectById(evaluation.getLectureId());
        if (lecture == null) {
            throw new ApiException("讲座不存在");
        }
        if (lecture.getStatus() == null || lecture.getStatus() != 3) {
            throw new ApiException("讲座结束后才能评价");
        }

        // 必须已确认报名该讲座
        Long regCount = registrationMapper.selectCount(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getLectureId, evaluation.getLectureId())
                .eq(Registration::getStatus, 1));
        if (regCount == null || regCount == 0) {
            throw new ApiException("只有报名参加过该讲座才能评价");
        }

        // 一人一评（数据库唯一键兜底，此处给出友好提示）
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getUserId, userId)
                .eq(Evaluation::getLectureId, evaluation.getLectureId()));
        if (exists != null && exists > 0) {
            throw new ApiException("你已评价过该讲座，请使用\u201c修改评价\u201d");
        }

        // 清理不可由客户端指定的字段
        evaluation.setId(null);
        if (evaluation.getContent() == null) {
            evaluation.setContent("");
        }
        evaluation.setSentimentScore(null);
        evaluation.setImprovementSuggestion(null);
        save(evaluation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEvaluation(Evaluation evaluation) {
        if (evaluation.getId() == null) {
            throw new ApiException("评价ID不能为空");
        }
        validateScore(evaluation.getScore());

        Evaluation existing = getById(evaluation.getId());
        if (existing == null) {
            throw new ApiException("评价不存在");
        }
        if (!currentUserId().equals(existing.getUserId())) {
            throw new ApiException("只能修改自己的评价");
        }

        // 仅允许修改评分与内容
        Evaluation update = new Evaluation();
        update.setId(existing.getId());
        update.setScore(evaluation.getScore());
        update.setContent(evaluation.getContent() == null ? "" : evaluation.getContent());
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvaluation(Long id) {
        Evaluation existing = getById(id);
        if (existing == null) {
            throw new ApiException("评价不存在");
        }
        if (!currentUserId().equals(existing.getUserId())) {
            throw new ApiException("只能删除自己的评价");
        }
        removeById(id);
    }

    private void validateScore(BigDecimal score) {
        if (score == null) {
            throw new ApiException("请选择评分");
        }
        if (score.compareTo(BigDecimal.valueOf(1)) < 0 || score.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new ApiException("评分需在 1~5 之间");
        }
    }

    private Long currentUserId() {
        if (!StpUtil.isLogin()) {
            throw new ApiException("请先登录");
        }
        return StpUtil.getLoginIdAsLong();
    }

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