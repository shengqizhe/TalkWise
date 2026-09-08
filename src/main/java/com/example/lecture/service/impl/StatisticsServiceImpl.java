package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.dto.StudentStatisticsDTO;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Registration;
import com.example.lecture.entity.User;
import com.example.lecture.mapper.RegistrationMapper;
import com.example.lecture.service.LectureService;
import com.example.lecture.service.RegistrationService;
import com.example.lecture.service.StatisticsService;
import com.example.lecture.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计数据Service实现类
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private LectureService lectureService;
    
    @Autowired
    private RegistrationService registrationService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RegistrationMapper registrationMapper;

    @Override
    public StudentStatisticsDTO getStudentStatistics(Long userId) {
        StudentStatisticsDTO statistics = new StudentStatisticsDTO();
        
        // 1. 获取已报名讲座数量（状态为已报名的记录）
        int registeredLectures = (int)registrationService.count(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getStatus, 1));
        statistics.setRegisteredLectures(registeredLectures);
        
        // 2. 获取所有可报名的讲座（状态为未开始且已发布的讲座）
        List<Lecture> availableLectures = lectureService.list(new LambdaQueryWrapper<Lecture>()
                .eq(Lecture::getStatus, 1) // 状态为未开始
                .eq(Lecture::getPublishStatus, 1)); // 已发布
        
        // 3. 获取用户已报名的讲座ID列表
        List<Long> registeredLectureIds = registrationService.list(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getStatus, 1))
                .stream()
                .map(Registration::getLectureId)
                .toList();
        
        // 4. 计算真正可报名的讲座数量（排除已报名的）
        int totalLectures = (int) availableLectures.stream()
                .filter(lecture -> !registeredLectureIds.contains(lecture.getId()))
                .count();
        statistics.setTotalLectures(totalLectures);
        
        // 5. 获取已参加讲座数量（已报名且讲座已结束）
        LocalDateTime now = LocalDateTime.now();
        List<Registration> registrations = registrationService.list(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getStatus, 1));
        
        List<Long> lectureIds = registrations.stream()
                .map(Registration::getLectureId)
                .toList();
        
        int attendedLectures = 0;
        if (!lectureIds.isEmpty()) {
            attendedLectures = (int) lectureService.count(new LambdaQueryWrapper<Lecture>()
                    .in(Lecture::getId, lectureIds)
                    .lt(Lecture::getLectureTime, now));
        }
        statistics.setAttendedLectures(attendedLectures);
        
        // 4. 获取参与度评分
        User user = userService.getById(userId);
        if (user != null && user.getParticipationScore() != null) {
            statistics.setParticipationScore(user.getParticipationScore().intValue());
        } else {
            statistics.setParticipationScore(0);
        }
        
        return statistics;
    }
}