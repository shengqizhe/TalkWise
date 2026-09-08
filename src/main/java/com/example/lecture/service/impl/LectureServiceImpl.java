package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.common.api.ResultCode;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.LectureCategory;
import com.example.lecture.entity.Location;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.service.LectureCategoryService;
import com.example.lecture.service.LectureService;
import com.example.lecture.service.LocationService;
import com.example.lecture.service.RegistrationService;
import com.example.lecture.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.lecture.dto.PromotionContentDTO;
import com.example.lecture.util.PromotionContentUtil;
import com.example.lecture.config.LectureReminderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 讲座Service实现类
 */
@Service
@Slf4j
public class LectureServiceImpl extends ServiceImpl<LectureMapper, Lecture> implements LectureService {
    
    @Autowired
    private LectureCategoryService lectureCategoryService;
    
    @Lazy
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private LocationService locationService;
    
    @Lazy
    @Autowired
    private RegistrationService registrationService;
    
    @Autowired
    private LectureReminderConfig lectureReminderConfig;
    
    @Autowired
    private com.example.lecture.service.EmailService emailService;
    
    @Autowired
    private com.example.lecture.service.UserService userService;
    
    @Autowired
    private com.example.lecture.service.RoleService roleService;


    @Override
    public Page<Lecture> page(Page<Lecture> page, String keyword, Long categoryId, Long organizerId, Integer publishStatus) {
        return page(page, keyword, categoryId, organizerId, publishStatus, null);
    }
    
    @Override
    public Page<Lecture> page(Page<Lecture> page, String keyword, Long categoryId, Long organizerId, Integer publishStatus, Integer status) {
        LambdaQueryWrapper<Lecture> wrapper = new LambdaQueryWrapper<Lecture>()
                .like(StringUtils.hasText(keyword), Lecture::getTitle, keyword)
                .or()
                .like(StringUtils.hasText(keyword), Lecture::getSpeaker, keyword)
                .eq(categoryId != null, Lecture::getCategoryId, categoryId)
                .eq(organizerId != null, Lecture::getOrganizerId, organizerId)
                .eq(publishStatus != null, Lecture::getPublishStatus, publishStatus)
                .eq(status != null, Lecture::getStatus, status)
                .eq(Lecture::getDeleted, 0)  // 只获取未删除的讲座（deleted=0）
                .orderByDesc(Lecture::getCreatedTime);
        
        Page<Lecture> resultPage = page(page, wrapper);
        // 为每个讲座添加分类名称和地点名称
        if (resultPage.getRecords() != null && !resultPage.getRecords().isEmpty()) {
            addCategoryNameToLectures(resultPage.getRecords());
            addLocationNameToLectures(resultPage.getRecords());
        }
        return resultPage;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Lecture lecture) {
        // 设置初始状态
        lecture.setStatus(1);
        lecture.setRegisteredCount(0);
        lecture.setPublishStatus(0); // 默认为未发布状态
        
        // 保存讲座
        save(lecture);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Lecture lecture) {
        // 检查讲座是否存在
        Lecture existLecture = getById(lecture.getId());
        if (existLecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }
        
        // 检查讲座状态
        if (existLecture.getStatus() == 3 || existLecture.getStatus() == 4) {
            throw new ApiException("讲座已结束或已取消，无法修改");
        }
        
        // 更新讲座
        updateById(lecture);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        // 检查讲座是否存在
        Lecture lecture = getById(id);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }
        
        // 检查讲座状态
        if (lecture.getStatus() == 3 || lecture.getStatus() == 4) {
            throw new ApiException("讲座已结束或已取消");
        }
        
        // 更新讲座状态
        lecture.setStatus(4);
        updateById(lecture);
        
        // 使用 LambdaUpdateWrapper 直接更新 deleted 字段
        LambdaUpdateWrapper<Lecture> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Lecture::getId, id)
                     .set(Lecture::getDeleted, 1);
        update(updateWrapper);
        
        // TODO: 发送取消通知
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        // 检查讲座是否存在
        Lecture lecture = getById(id);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }
        
        // 检查状态转换是否合法
        Integer currentStatus = lecture.getStatus();
        
        // 状态转换规则
        if (status == 1) {
            // 恢复到未开始状态 - 允许从已结束(3)或已取消(4)状态恢复
            if (currentStatus != 3 && currentStatus != 4 && currentStatus != 2) {
                throw new ApiException("只有进行中、已结束或已取消的讲座才能恢复到未开始状态");
            }
            
            // 如果从已结束状态恢复到未开始状态，重置所有报名的签到状态
            if (currentStatus == 3) {
                resetRegistrationCheckinStatus(id);
                log.info("讲座[{}]从已结束状态恢复到未开始状态，已重置所有报名的签到状态", lecture.getTitle());
            }
        } else if (status == 2) {
            // 开始讲座 - 只有未开始(1)的讲座才能变为进行中(2)
            if (currentStatus != 1) {
                throw new ApiException("只有未开始的讲座才能开始");
            }
            
            // 如果讲座状态变为进行中，确保发布状态为已发布
            if (lecture.getPublishStatus() != 1) {
                baseMapper.updatePublishStatus(id, 1);
                log.info("讲座[{}]状态变为进行中时，发布状态已自动更新为已发布", lecture.getTitle());
            }
        } else if (status == 3) {
            // 结束讲座 - 只有进行中(2)的讲座才能变为已结束(3)
            if (currentStatus != 2) {
                throw new ApiException("只有进行中的讲座才能结束");
            }
            
            // 如果讲座状态变为已结束，确保发布状态为已发布
            if (lecture.getPublishStatus() != 1) {
                baseMapper.updatePublishStatus(id, 1);
                log.info("讲座[{}]状态变为已结束时，发布状态已自动更新为已发布", lecture.getTitle());
            }
        } else {
            throw new ApiException("无效的讲座状态");
        }
        
        // 更新讲座状态
        baseMapper.updateStatus(id, status);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePublishStatus(Long id, Integer publishStatus) {
        // 检查讲座是否存在
        Lecture lecture = getById(id);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }
        
        // 检查发布状态值是否有效
        if (publishStatus != 0 && publishStatus != 1) {
            throw new ApiException("无效的发布状态");
        }
        
        // 如果是取消发布操作，检查讲座状态
        if (publishStatus == 0) {
            // 检查讲座是否已开始或已结束
            if (lecture.getStatus() == 2) {
                throw new ApiException("进行中的讲座不能取消发布");
            } else if (lecture.getStatus() == 3) {
                throw new ApiException("已结束的讲座不能取消发布");
            }
        }
        
        // 更新讲座发布状态
        baseMapper.updatePublishStatus(id, publishStatus);
    }
    
    @Override
    public void updateRegisteredCount(Long id, int count) {
        // 直接更新报名人数，不再检查讲座是否存在
        // 因为在调用此方法前，已经检查过讲座是否存在
        // 减少一次数据库查询操作
        baseMapper.updateRegisteredCount(id, count);
    }
    
    /**
     * 重写getById方法，添加分类名称和地点名称
     */
    @Override
    public Lecture getById(Long id) {
        Lecture lecture = super.getById(id);
        if (lecture != null) {
            // 为讲座添加分类名称
            addCategoryNameToLecture(lecture);
            // 为讲座添加地点名称
            addLocationNameToLecture(lecture);
        }
        return lecture;
    }

    /**
     * 检查数据是否自上次时间戳以来有变化
     * 优化版本：直接查询数据库，避免遍历所有讲座
     *
     * @param lectures             当前讲座列表
     * @param lastUpdatedTimestamp 上次更新时间戳（毫秒）
     * @return 如果数据有变化返回true，否则返回false
     */
    @Override
    public boolean hasDataChangedSince(List<Lecture> lectures, Long lastUpdatedTimestamp) {
        if (lectures == null || lectures.isEmpty()) {
            return false;
        }

        // 如果时间戳无效，认为数据有变化
        if (lastUpdatedTimestamp == null || lastUpdatedTimestamp <= 0) {
            return true;
        }

        try {
            // 转换时间戳为LocalDateTime
            LocalDateTime lastUpdateTime = LocalDateTime.ofEpochSecond(
                    lastUpdatedTimestamp / 1000,
                    (int) ((lastUpdatedTimestamp % 1000) * 1000000),
                    ZoneOffset.UTC
            );

            // 获取所有讲座ID
            List<Long> lectureIds = lectures.stream()
                    .map(Lecture::getId)
                    .collect(Collectors.toList());

            if (lectureIds.isEmpty()) {
                return false;
            }

            // 直接查询数据库，检查是否有任何讲座的更新时间晚于上次更新时间
            // 这比遍历内存中的讲座列表更高效，尤其是当讲座数量较多时
            LambdaQueryWrapper<Lecture> queryWrapper = new LambdaQueryWrapper<Lecture>()
                    .in(Lecture::getId, lectureIds)
                    .gt(Lecture::getUpdatedTime, lastUpdateTime);

            // 只需要检查是否存在符合条件的记录，不需要获取完整数据
            return baseMapper.exists(queryWrapper);
        } catch (Exception e) {
            log.error("检查数据变化时出错：", e);
            // 出错时默认认为数据有变化，确保前端能获取最新数据
            return true;
        }
    }
    
    /**
     * 为讲座列表添加分类名称
     */
    private void addCategoryNameToLectures(List<Lecture> lectures) {
        if (lectures == null || lectures.isEmpty()) {
            return;
        }
        
        // 获取所有分类ID
        Set<Long> categoryIds = lectures.stream()
                .map(Lecture::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        if (categoryIds.isEmpty()) {
            return;
        }
        
        // 查询所有相关分类
        List<LectureCategory> categories = lectureCategoryService.listByIds(categoryIds);
        Map<Long, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(LectureCategory::getId, LectureCategory::getCategoryName));
        
        // 为每个讲座设置分类名称
        lectures.forEach(lecture -> {
            if (lecture.getCategoryId() != null) {
                String categoryName = categoryMap.get(lecture.getCategoryId());
                lecture.setCategoryName(categoryName);
            }
        });
    }
    
    /**
     * 为单个讲座添加分类名称
     */
    private void addCategoryNameToLecture(Lecture lecture) {
        if (lecture == null || lecture.getCategoryId() == null) {
            return;
        }
        
        // 查询分类
        LectureCategory category = lectureCategoryService.getById(lecture.getCategoryId());
        if (category != null) {
            lecture.setCategoryName(category.getCategoryName());
        }
    }
    
    /**
     * 为讲座列表添加地点名称
     */
    private void addLocationNameToLectures(List<Lecture> lectures) {
        if (lectures == null || lectures.isEmpty()) {
            return;
        }

        // 获取所有地点ID
        Set<Long> locationIds = lectures.stream()
                .map(Lecture::getLocationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (locationIds.isEmpty()) {
            return;
        }

        // 查询所有相关地点
        List<Location> locations = locationService.listByIds(locationIds);
        Map<Long, String> locationMap = locations.stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));

        // 为每个讲座设置地点名称
        lectures.forEach(lecture -> {
            if (lecture.getLocationId() != null) {
                String locationName = locationMap.get(lecture.getLocationId());
                // 将地点名称存储到扩展数据中
                lecture.getExtendData().put("locationName", locationName);
            }
        });
    }

    /**
     * 为单个讲座添加地点名称
     */
    private void addLocationNameToLecture(Lecture lecture) {
        if (lecture == null || lecture.getLocationId() == null) {
            return;
        }

        // 查询地点
        Location location = locationService.getById(lecture.getLocationId());
        if (location != null) {
            // 将地点名称存储到扩展数据中
            lecture.getExtendData().put("locationName", location.getName());
        }
    }
    
    /**
     * 定时任务：每分钟检查一次讲座状态，自动更新讲座状态
     * 1. 发送即将开始提醒（提前15分钟）
     * 2. 将已到开始时间但仍为"未开始"状态的讲座更新为"进行中"
     * 3. 将已超过结束时间但仍为"进行中"状态的讲座更新为"已结束"
     */
    @Scheduled(cron = "0 * * * * ?")
    public void autoUpdateLectureStatus() {
        log.info("开始自动更新讲座状态...");
        try {
            // 当前时间
            LocalDateTime now = LocalDateTime.now();
            
            // 1. 发送即将开始提醒（提前15分钟）
            sendUpcomingLectureReminders(now);

            // 2. 更新已到开始时间的讲座状态为"进行中"
            QueryWrapper<Lecture> startingWrapper = new QueryWrapper<>();
            startingWrapper.le("lecture_time", now); // 讲座开始时间小于等于当前时间
            startingWrapper.eq("status", 1); // 未开始状态
            startingWrapper.eq("publish_status", 1); // 已发布状态

            List<Lecture> startingLectures = baseMapper.selectList(startingWrapper);
            for (Lecture lecture : startingLectures) {
                // 更新讲座状态为"进行中"
                baseMapper.updateStatus(lecture.getId(), 2);
                log.info("讲座[{}]已自动更新为进行中状态", lecture.getTitle());

                // 确保讲座发布状态为已发布
                if (lecture.getPublishStatus() != 1) {
                    baseMapper.updatePublishStatus(lecture.getId(), 1);
                    log.info("讲座[{}]的发布状态已自动更新为已发布", lecture.getTitle());
                }

                // 可以发送讲座开始通知
                webSocketService.sendLectureNotification(
                        lecture.getId(),
                        "讲座《" + lecture.getTitle() + "》已开始，地点：" + lecture.getLocationId()
                );
            }

            // 3. 更新已超过结束时间的讲座状态为"已结束"
            // 讲座默认时长为2小时
            final int DEFAULT_LECTURE_DURATION_MINUTES = 120;

            // 查询所有讲座（包括未开始和进行中的讲座）
            QueryWrapper<Lecture> endingWrapper = new QueryWrapper<>();
            endingWrapper.in("status", 1, 2); // 未开始状态(1)或进行中状态(2)

            List<Lecture> activeLectures = baseMapper.selectList(endingWrapper);
            List<Lecture> endingLectures = activeLectures.stream()
                    .filter(lecture -> {
                        // 计算讲座结束时间（开始时间 + 默认时长）
                        LocalDateTime endTime = lecture.getLectureTime().plusMinutes(DEFAULT_LECTURE_DURATION_MINUTES);
                        // 如果当前时间已经超过结束时间，则需要更新状态
                        return now.isAfter(endTime);
                    })
                    .collect(Collectors.toList());
            for (Lecture lecture : endingLectures) {
                // 更新讲座状态为"已结束"
                baseMapper.updateStatus(lecture.getId(), 3);
                log.info("讲座[{}]已自动更新为已结束状态", lecture.getTitle());

                // 可以发送讲座结束通知
                webSocketService.sendLectureNotification(
                        lecture.getId(),
                        "讲座《" + lecture.getTitle() + "》已结束"
                );
            }

            log.info("自动更新讲座状态完成：{}个讲座开始，{}个讲座结束", startingLectures.size(), endingLectures.size());
        } catch (Exception e) {
            log.error("自动更新讲座状态出错：", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePromotionContent(Long lectureId, PromotionContentDTO promotionContent) {
        // 检查讲座是否存在
        Lecture lecture = getById(lectureId);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }

        // 检查讲座状态，已结束或已取消的讲座不能修改宣讲图片
        if (lecture.getStatus() == 3 || lecture.getStatus() == 4) {
            throw new ApiException("讲座已结束或已取消，无法修改宣讲图片");
        }

        // 验证和清理宣讲图片
        PromotionContentDTO sanitizedContent = PromotionContentUtil.sanitizePromotionContent(promotionContent);
        PromotionContentUtil.validatePromotionContent(sanitizedContent);

        // 将 DTO 转换为 JSON 字符串存储到数据库
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String promotionContentJson = objectMapper.writeValueAsString(sanitizedContent);

            // 更新宣讲图片
            lecture.setPromotionContent(promotionContentJson);
            updateById(lecture);

            log.info("讲座[{}]的宣讲图片已更新", lecture.getTitle());
        } catch (Exception e) {
            log.error("宣讲图片序列化失败", e);
            throw new ApiException("宣讲图片格式转换失败: " + e.getMessage());
        }
    }

    @Override
    public PromotionContentDTO getPromotionContent(Long lectureId) {
        // 检查讲座是否存在
        Lecture lecture = getById(lectureId);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }

        String promotionContentJson = lecture.getPromotionContent();
        if (promotionContentJson == null || promotionContentJson.trim().isEmpty()) {
            return new PromotionContentDTO();
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(promotionContentJson, PromotionContentDTO.class);
        } catch (Exception e) {
            log.error("宣讲图片反序列化失败", e);
            return new PromotionContentDTO();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePromotionContent(Long lectureId) {
        // 检查讲座是否存在
        Lecture lecture = getById(lectureId);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }

        // 检查讲座状态，已结束或已取消的讲座不能修改宣讲图片
        if (lecture.getStatus() == 3 || lecture.getStatus() == 4) {
            throw new ApiException("讲座已结束或已取消，无法删除宣讲图片");
        }

        // 清空宣讲图片
        lecture.setPromotionContent(null);
        updateById(lecture);

        log.info("讲座[{}]的宣讲图片已删除", lecture.getTitle());
    }
    
    /**
     * 重置指定讲座的所有报名签到状态
     * 当讲座从已结束状态恢复到未开始状态时调用
     * 
     * @param lectureId 讲座ID
     */
    private void resetRegistrationCheckinStatus(Long lectureId) {
        try {
            // 调用RegistrationService的方法重置签到状态
            registrationService.resetCheckinStatusByLectureId(lectureId);
            log.info("已重置讲座[{}]的所有报名签到状态", lectureId);
        } catch (Exception e) {
            log.error("重置讲座[{}]的报名签到状态失败：", lectureId, e);
            throw new ApiException("重置报名签到状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送即将开始的讲座提醒
     * 根据配置的提醒时间发送邮件和WebSocket通知给已报名的用户
     * 支持动态提醒时间计算，根据讲座开始时间确定提醒时间
     * 使用精确的时间窗口避免重复发送
     * 
     * @param now 当前时间
     */
    private void sendUpcomingLectureReminders(LocalDateTime now) {
        try {
            int windowSeconds = lectureReminderConfig.getWindowSeconds();
            
            // 如果启用了动态提醒，需要查询更大范围的讲座，然后逐个判断
            List<Lecture> candidateLectures;
            
            if (lectureReminderConfig.isDynamicReminderEnabled()) {
                // 动态提醒模式：查询未来最大提醒时间范围内的所有讲座
                int maxReminderMinutes = lectureReminderConfig.getMaxReminderMinutes();
                LocalDateTime maxStartWindow = now.plusMinutes(maxReminderMinutes - 1);
                LocalDateTime maxEndWindow = now.plusMinutes(lectureReminderConfig.getMinReminderMinutes() + 1);
                
                QueryWrapper<Lecture> dynamicWrapper = new QueryWrapper<>();
                dynamicWrapper.between("lecture_time", maxEndWindow, maxStartWindow);
                dynamicWrapper.eq("status", 1); // 未开始状态
                dynamicWrapper.eq("publish_status", 1); // 已发布状态
                
                candidateLectures = baseMapper.selectList(dynamicWrapper);
            } else {
                // 固定提醒模式：使用原有逻辑
                int reminderMinutes = lectureReminderConfig.getReminderMinutes();
                LocalDateTime startWindow = now.plusMinutes(reminderMinutes).minusSeconds(windowSeconds);
                LocalDateTime endWindow = now.plusMinutes(reminderMinutes).plusSeconds(windowSeconds);
                
                QueryWrapper<Lecture> fixedWrapper = new QueryWrapper<>();
                fixedWrapper.between("lecture_time", startWindow, endWindow);
                fixedWrapper.eq("status", 1); // 未开始状态
                fixedWrapper.eq("publish_status", 1); // 已发布状态
                
                candidateLectures = baseMapper.selectList(fixedWrapper);
            }
            
            // 过滤出真正需要提醒的讲座
            List<Lecture> upcomingLectures = candidateLectures.stream()
                .filter(lecture -> shouldSendReminder(lecture, now, windowSeconds))
                .collect(Collectors.toList());
            
            if (upcomingLectures.isEmpty()) {
                return; // 没有需要提醒的讲座
            }
            
            log.info("开始发送即将开始提醒，共找到 {} 个讲座需要提醒", upcomingLectures.size());
            
            for (Lecture lecture : upcomingLectures) {
                try {
                    // 计算距离开始的精确时间
                    long minutesUntilStart = java.time.Duration.between(now, lecture.getLectureTime()).toMinutes();
                    
                    // 准备邮件所需的信息
                    String startTime = lecture.getLectureTime() != null ? 
                        lecture.getLectureTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "待定";
                    String location = "待定";
                    
                    // 获取地点信息
                    if (lecture.getLocationId() != null) {
                        Location locationEntity = locationService.getById(lecture.getLocationId());
                        if (locationEntity != null) {
                            location = locationEntity.getName();
                        }
                    }
                    
                    // 计算动态提醒时间
                    int actualReminderMinutes;
                    if (lectureReminderConfig.isDynamicReminderEnabled()) {
                        actualReminderMinutes = lectureReminderConfig.calculateDynamicReminderMinutes(lecture.getLectureTime());
                    } else {
                        actualReminderMinutes = lectureReminderConfig.getReminderMinutes();
                    }
                    
                    // 发送即将开始通知（包含邮件，使用动态提醒时间）
                    sendUpcomingLectureNotificationWithDynamicReminder(
                        lecture.getId(),
                        lecture.getTitle(),
                        startTime,
                        location,
                        actualReminderMinutes
                    );
                    
                    log.info("✅ 已发送即将开始提醒 - 讲座: {}, 开始时间: {}, 距离开始: {}分钟, 提醒设置: {}分钟前", 
                            lecture.getTitle(), startTime, minutesUntilStart, lectureReminderConfig.getReminderMinutes());
                    
                } catch (Exception e) {
                    log.error("❌ 发送即将开始提醒失败 - 讲座ID: {}, 讲座标题: {}, 错误: {}", 
                        lecture.getId(), lecture.getTitle(), e.getMessage(), e);
                }
            }
            
            log.info("🎯 即将开始提醒发送完成，成功处理 {} 个讲座", upcomingLectures.size());
            
        } catch (Exception e) {
            log.error("💥 发送即将开始提醒时出现系统错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送即将开始的讲座通知（支持动态提醒时间）
     * 
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 地点
     * @param reminderMinutes 提醒时间（分钟）
     */
    private void sendUpcomingLectureNotificationWithDynamicReminder(Long lectureId, String lectureTitle, String startTime, String location, int reminderMinutes) {
        try {
            // 发送WebSocket通知
            webSocketService.sendUpcomingLectureNotification(lectureId, lectureTitle, startTime, location);
            log.info("📡 WebSocket通知发送成功 - 讲座: {}", lectureTitle);
            
            // 1. 向讲师发送提醒邮件
            sendTeacherReminder(lectureId, lectureTitle, startTime, location, reminderMinutes);
            
            // 2. 获取所有报名用户并发送邮件
            List<com.example.lecture.dto.RegistrationUserDTO> registrations = registrationService.getLectureRegistrations(lectureId);
            
            if (registrations == null || registrations.isEmpty()) {
                log.info("📧 该讲座暂无报名用户，跳过学生邮件发送 - 讲座: {}", lectureTitle);
                return;
            }
            
            // 统计信息
            int totalRegistrations = registrations.size();
            int activeRegistrations = (int) registrations.stream().filter(r -> r.getStatus() == 1).count();
            int emailsSent = 0;
            int emailsFailed = 0;
            
            log.info("📧 开始发送邮件提醒 - 讲座: {}, 总报名数: {}, 有效报名数: {}, 提醒时间: {}分钟", 
                    lectureTitle, totalRegistrations, activeRegistrations, reminderMinutes);
            
            for (com.example.lecture.dto.RegistrationUserDTO registration : registrations) {
                 // 只给已报名状态的用户发送邮件
                 if (registration.getStatus() == 1) {
                     com.example.lecture.entity.User user = userService.getById(registration.getUserId());
                     if (user != null) {
                         try {
                             // 使用支持动态提醒时间的邮件发送方法
                             if (emailService instanceof com.example.lecture.service.impl.EmailServiceImpl) {
                                 ((com.example.lecture.service.impl.EmailServiceImpl) emailService)
                                     .sendUpcomingLectureEmail(user, lectureTitle, startTime, location, reminderMinutes);
                             } else {
                                 // 回退到默认方法
                                 emailService.sendUpcomingLectureEmail(user, lectureTitle, startTime, location);
                             }
                             emailsSent++;
                             log.debug("✅ 邮件发送成功 - 用户: {} ({}), 提醒时间: {}分钟", user.getRealName(), user.getEmail(), reminderMinutes);
                         } catch (Exception emailException) {
                             emailsFailed++;
                             log.warn("❌ 邮件发送失败 - 用户: {} ({}), 错误: {}", 
                                 user.getRealName(), user.getEmail(), emailException.getMessage());
                         }
                     } else {
                         emailsFailed++;
                         log.warn("❌ 用户信息获取失败 - 用户ID: {}", registration.getUserId());
                     }
                 } else {
                     log.debug("⏭️ 跳过非活跃报名 - 用户ID: {}, 状态: {}", 
                             registration.getUserId(), registration.getStatus());
                 }
             }
            
            log.info("🎯 即将开始通知发送完成 - 讲座: {}, 邮件发送成功: {}, 邮件发送失败: {}, 总处理数: {}, 提醒时间: {}分钟", 
                    lectureTitle, emailsSent, emailsFailed, activeRegistrations, reminderMinutes);
                    
        } catch (Exception e) {
            log.error("💥 发送即将开始通知失败 - 讲座: {}, 错误: {}", lectureTitle, e.getMessage(), e);
        }
    }
    
    /**
     * 判断是否应该为指定讲座发送提醒
     * 支持动态提醒时间计算
     * 
     * @param lecture 讲座信息
     * @param now 当前时间
     * @param windowSeconds 时间窗口（秒）
     * @return 是否应该发送提醒
     */
    private boolean shouldSendReminder(Lecture lecture, LocalDateTime now, int windowSeconds) {
        LocalDateTime lectureTime = lecture.getLectureTime();
        
        if (lectureReminderConfig.isDynamicReminderEnabled()) {
            // 动态提醒模式：根据讲座开始时间计算提醒时间
            int dynamicReminderMinutes = lectureReminderConfig.calculateDynamicReminderMinutes(lectureTime);
            
            // 计算动态提醒的时间窗口
            LocalDateTime reminderTime = lectureTime.minusMinutes(dynamicReminderMinutes);
            LocalDateTime startWindow = reminderTime.minusSeconds(windowSeconds);
            LocalDateTime endWindow = reminderTime.plusSeconds(windowSeconds);
            
            // 检查当前时间是否在提醒窗口内
            return now.isAfter(startWindow) && now.isBefore(endWindow);
        } else {
            // 固定提醒模式：使用配置的固定提醒时间
            int reminderMinutes = lectureReminderConfig.getReminderMinutes();
            LocalDateTime reminderTime = lectureTime.minusMinutes(reminderMinutes);
            LocalDateTime startWindow = reminderTime.minusSeconds(windowSeconds);
            LocalDateTime endWindow = reminderTime.plusSeconds(windowSeconds);
            
            // 检查当前时间是否在提醒窗口内
            return now.isAfter(startWindow) && now.isBefore(endWindow);
        }
    }
    
    /**
     * 向讲师发送讲座即将开始的提醒邮件
     * 
     * @param lectureId 讲座ID
     * @param lectureTitle 讲座标题
     * @param startTime 开始时间
     * @param location 地点
     * @param reminderMinutes 提醒时间（分钟）
     */
    private void sendTeacherReminder(Long lectureId, String lectureTitle, String startTime, String location, int reminderMinutes) {
        try {
            // 获取讲座信息
            Lecture lecture = getById(lectureId);
            if (lecture == null || lecture.getOrganizerId() == null) {
                log.warn("讲座不存在或没有组织者，跳过讲师提醒 - 讲座ID: {}", lectureId);
                return;
            }
            
            // 获取组织者（讲师）信息
            com.example.lecture.entity.User organizer = userService.getById(lecture.getOrganizerId());
            if (organizer == null) {
                log.warn("组织者不存在，跳过讲师提醒 - 组织者ID: {}", lecture.getOrganizerId());
                return;
            }
            
            // 验证组织者是否具有讲师角色
             List<com.example.lecture.entity.Role> roles = roleService.getRolesByUserId(organizer.getId());
             boolean isTeacher = roles.stream()
                     .anyMatch(role -> "teacher".equalsIgnoreCase(role.getRoleName()));
             
             if (!isTeacher) {
                 log.debug("组织者不是讲师角色，跳过讲师提醒 - 用户: {} ({})", organizer.getRealName(), organizer.getEmail());
                 return;
             }
             
             // 发送讲师提醒邮件
             if (emailService instanceof com.example.lecture.service.impl.EmailServiceImpl) {
                 ((com.example.lecture.service.impl.EmailServiceImpl) emailService)
                     .sendTeacherLectureReminderEmail(organizer, lectureTitle, startTime, location, reminderMinutes);
             } else {
                 // 回退到通用方法
                 emailService.sendUpcomingLectureEmail(organizer, lectureTitle, startTime, location);
             }
            
            log.info("✅ 讲师提醒邮件发送成功 - 讲师: {} ({}), 讲座: {}, 提醒时间: {}分钟", 
                    organizer.getRealName(), organizer.getEmail(), lectureTitle, reminderMinutes);
                    
        } catch (Exception e) {
            log.error("❌ 讲师提醒邮件发送失败 - 讲座: {}, 错误: {}", lectureTitle, e.getMessage(), e);
        }
    }
}