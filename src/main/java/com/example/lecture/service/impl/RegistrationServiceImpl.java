package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.common.api.ResultCode;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.dto.RegistrationLectureDTO;
import com.example.lecture.dto.RegistrationUserDTO;
import com.example.lecture.entity.*;
import com.example.lecture.mapper.RegistrationMapper;
import com.example.lecture.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报名记录Service实现类
 */
@Slf4j
@Service
public class RegistrationServiceImpl extends ServiceImpl<RegistrationMapper, Registration> implements RegistrationService {

    @Lazy
    @Autowired
    private LectureService lectureService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private DepartmentService departmentService;
    
    @Autowired
    private LectureCategoryService lectureCategoryService;

    @Autowired
    private LocationService locationService;

    @Lazy
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private EmailService emailService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Long userId, Long lectureId) {
        // 检查讲座是否存在且已发布 - 使用单次查询获取讲座信息
        Lecture lecture = lectureService.getById(lectureId);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }

        // 一次性检查所有讲座状态条件
        if (lecture.getPublishStatus() != 1) {
            throw new ApiException("讲座尚未发布，不能报名");
        }

        if (lecture.getStatus() != 1) { // 1表示未开始
            throw new ApiException("只能报名未开始状态的讲座");
        }
        
        if (lecture.getRegisteredCount() >= lecture.getCapacity()) {
            throw new ApiException("讲座已满员");
        }

        // 使用更高效的方式检查用户是否已报名
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getLectureId, lectureId)
                .eq(Registration::getStatus, 1)); // 只检查状态为1(已报名)的记录

        if (count > 0) {
            throw new ApiException(ResultCode.ALREADY_REGISTERED);
        }

        try {
            // 检查是否存在已取消的报名记录
            Registration existingRegistration = lambdaQuery()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getLectureId, lectureId)
                .eq(Registration::getStatus, 2) // 状态为2表示已取消
                .one();

            boolean saveSuccess = false;

            if (existingRegistration != null) {
                // 如果存在已取消的记录，则更新状态为已报名
                existingRegistration.setStatus(1);
                existingRegistration.setRegisterTime(LocalDateTime.now());
                existingRegistration.setCancelTime(null);
                saveSuccess = updateById(existingRegistration);
            } else {
                // 创建新的报名记录
                Registration registration = new Registration();
                registration.setUserId(userId);
                registration.setLectureId(lectureId);
                registration.setStatus(1);
                registration.setRegisterTime(LocalDateTime.now());

                saveSuccess = save(registration);
            }

            if (saveSuccess) {
                // 更新讲座报名人数
                lectureService.updateRegisteredCount(lectureId, 1);
                
                // 发送WebSocket通知和邮件
                try {
                    // 获取学生信息
                    User student = userService.getById(userId);
                    
                    // 1. 发送报名成功通知给学生（包含邮件）
                    String startTime = lecture.getLectureTime() != null ? lecture.getLectureTime().toString() : "待定";
                    String location = lecture.getLocationId() != null ? 
                        locationService.getById(lecture.getLocationId()).getName() : "待定";
                    
                    webSocketService.sendRegistrationSuccessNotificationWithEmail(
                        userId, lectureId, lecture.getTitle(), startTime, location);
                    
                    // 2. 发送报名通知给教师（WebSocket + 邮件）
                    if (student != null && lecture.getOrganizerId() != null) {
                        String studentName = student.getRealName() != null ? student.getRealName() : student.getUsername();
                        
                        // 发送WebSocket通知
                        webSocketService.sendStudentRegistrationNotificationToTeacher(
                            lecture.getOrganizerId(), 
                            lectureId, 
                            lecture.getTitle(),
                            studentName
                        );
                        
                        // 发送邮件通知给教师
                        try {
                            User teacher = userService.getById(lecture.getOrganizerId());
                            if (teacher != null) {
                                emailService.sendStudentRegistrationNotificationEmail(
                                    teacher, 
                                    lecture.getTitle(), 
                                    studentName, 
                                    startTime, 
                                    location
                                );
                            }
                        } catch (Exception emailException) {
                            log.warn("发送教师邮件通知失败: {}", emailException.getMessage());
                        }
                    }
                    
                    log.info("报名成功通知已发送 - 学生ID: {}, 讲座ID: {}, 讲座标题: {}", 
                        userId, lectureId, lecture.getTitle());
                        
                } catch (Exception e) {
                    log.warn("发送通知失败: {}", e.getMessage());
                    // 通知发送失败不影响报名流程
                }
            } else {
                throw new ApiException("报名失败，请稍后重试");
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 处理唯一键冲突异常，说明用户已经报名过该讲座
            throw new ApiException(ResultCode.ALREADY_REGISTERED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long lectureId) {
        // 使用一次查询获取报名记录和讲座信息
        Registration registration = baseMapper.selectOne(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .eq(Registration::getLectureId, lectureId));
        
        if (registration == null) {
            throw new ApiException(ResultCode.NOT_REGISTERED);
        }

        // 检查讲座状态 - 使用更高效的查询
        Lecture lecture = lectureService.getById(lectureId);
        if (lecture == null) {
            throw new ApiException(ResultCode.LECTURE_NOT_EXIST);
        }
        
        // 只允许取消未开始状态的讲座报名
        if (lecture.getStatus() != 1) { // 1表示未开始
            if (lecture.getStatus() == 2) { // 2表示进行中
                throw new ApiException("讲座正在进行中，无法取消报名");
            } else {
                throw new ApiException("只能取消未开始状态的讲座报名");
            }
        }
        
        // 更新报名状态为已取消
        registration.setStatus(2); // 2表示已取消
        registration.setCancelTime(LocalDateTime.now());

        // 使用批量更新操作，减少数据库交互
        boolean updateSuccess = updateById(registration);

        if (updateSuccess) {
            // 减少讲座报名人数
            lectureService.updateRegisteredCount(lectureId, -1);
            
            // 发送取消报名通知
            try {
                // 获取学生信息
                User student = userService.getById(userId);
                
                // 准备邮件所需的信息
                String startTime = lecture.getLectureTime() != null ? lecture.getLectureTime().toString() : "待定";
                String location = lecture.getLocationId() != null ? 
                    locationService.getById(lecture.getLocationId()).getName() : "待定";
                
                // 1. 发送取消报名通知给学生（WebSocket + 邮件）
                webSocketService.sendRegistrationCancelNotification(
                    userId, lectureId, lecture.getTitle());
                
                // 发送取消报名邮件给学生
                if (student != null) {
                    try {
                        emailService.sendRegistrationCancelEmail(
                            student, 
                            lecture.getTitle(), 
                            startTime, 
                            location
                        );
                    } catch (Exception emailException) {
                        log.warn("发送学生取消报名邮件失败: {}", emailException.getMessage());
                    }
                }
                
                // 2. 发送学生取消报名通知给教师（WebSocket + 邮件）
                if (student != null && lecture.getOrganizerId() != null) {
                    String studentName = student.getRealName() != null ? 
                        student.getRealName() : student.getUsername();
                    
                    // 发送WebSocket通知给教师
                    webSocketService.sendStudentCancelNotificationToTeacher(
                        lecture.getOrganizerId(), 
                        lectureId, 
                        lecture.getTitle(),
                        studentName
                    );
                    
                    // 发送邮件通知给教师
                    try {
                        User teacher = userService.getById(lecture.getOrganizerId());
                        if (teacher != null) {
                            emailService.sendStudentCancelNotificationEmail(
                                teacher, 
                                lecture.getTitle(), 
                                studentName, 
                                startTime, 
                                location
                            );
                        }
                    } catch (Exception emailException) {
                        log.warn("发送教师取消报名邮件通知失败: {}", emailException.getMessage());
                    }
                }
                
                log.info("取消报名通知已发送 - 学生ID: {}, 讲座ID: {}, 讲座标题: {}", 
                    userId, lectureId, lecture.getTitle());
                    
            } catch (Exception e) {
                log.warn("发送取消报名通知失败: {}", e.getMessage());
                // 通知发送失败不影响取消报名流程
            }
        } else {
            throw new ApiException("取消报名失败，请稍后重试");
        }
    }

    @Override
    public boolean isRegistered(Long userId, Long lectureId) {
        return baseMapper.countByUserIdAndLectureId(userId, lectureId) > 0;
    }
    
    @Override
    public List<RegistrationUserDTO> getLectureRegistrations(Long lectureId) {
        // 0. 获取讲座信息
        Lecture lecture = lectureService.getById(lectureId);
        if (lecture == null) {
            return new ArrayList<>();
        }
        
        // 1. 获取讲座的所有报名记录
        List<Registration> registrations = list(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getLectureId, lectureId)
                .orderByDesc(Registration::getRegisterTime));
        
        if (registrations.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 2. 获取所有报名用户的ID
        List<Long> userIds = registrations.stream()
                .map(Registration::getUserId)
                .collect(Collectors.toList());
        
        // 3. 批量查询用户信息
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        
        // 4. 获取所有系别ID
        List<Long> departmentIds = users.stream()
                .map(User::getDepartmentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        // 5. 批量查询系别信息
        Map<Long, String> departmentMap = new java.util.HashMap<>();
        if (!departmentIds.isEmpty()) {
            List<Department> departments = departmentService.listByIds(departmentIds);
            departmentMap = departments.stream()
                    .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
        }
        
        // 6. 组装返回结果
        List<RegistrationUserDTO> result = new ArrayList<>();
        for (Registration registration : registrations) {
            User user = userMap.get(registration.getUserId());
            if (user != null) {
                RegistrationUserDTO dto = new RegistrationUserDTO();
                dto.setId(registration.getId());
                dto.setUserId(user.getId());
                dto.setUsername(user.getUsername());
                dto.setRealName(user.getRealName());
                dto.setStudentTeacherId(user.getStudentTeacherId());
                dto.setEmail(user.getEmail());
                dto.setPhone(user.getPhone());
                dto.setDepartmentId(user.getDepartmentId());
                
                // 设置系别名称
                if (user.getDepartmentId() != null) {
                    dto.setDepartmentName(departmentMap.get(user.getDepartmentId()));
                }
                
                dto.setRegisterTime(registration.getRegisterTime());
                dto.setCancelTime(registration.getCancelTime());
                dto.setStatus(registration.getStatus());
                dto.setPublishStatus(lecture.getPublishStatus()); // 设置讲座发布状态
                dto.setLectureStatus(lecture.getStatus()); // 设置讲座状态
                dto.setCheckinStatus(registration.getCheckinStatus() != null ? registration.getCheckinStatus() : 0); // 设置签到状态
                
                result.add(dto);
            }
        }
        
        return result;
    }
    
    @Override
    public List<RegistrationLectureDTO> getUserRegistrations(Long userId, Integer status) {
        // 1. 构建查询条件
        LambdaQueryWrapper<Registration> queryWrapper = new LambdaQueryWrapper<Registration>()
                .eq(Registration::getUserId, userId)
                .orderByDesc(Registration::getRegisterTime);
        
        // 如果指定了状态，则添加状态条件
        if (status != null) {
            queryWrapper.eq(Registration::getStatus, status);
        }
        
        // 2. 获取用户的所有报名记录
        List<Registration> registrations = list(queryWrapper);
        
        if (registrations.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 3. 获取所有讲座ID
        List<Long> lectureIds = registrations.stream()
                .map(Registration::getLectureId)
                .collect(Collectors.toList());
        
        // 4. 批量查询讲座信息
        List<Lecture> lectures = lectureService.listByIds(lectureIds);
        Map<Long, Lecture> lectureMap = lectures.stream()
                .collect(Collectors.toMap(Lecture::getId, lecture -> lecture));
        
        // 4.1 获取所有地点ID并批量查询地点信息
        Set<Long> locationIds = lectures.stream()
                .map(Lecture::getLocationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> locationMap = new HashMap<>();
        if (!locationIds.isEmpty()) {
            List<Location> locations = locationService.listByIds(new ArrayList<>(locationIds));
            locationMap = locations.stream()
                    .collect(Collectors.toMap(Location::getId, Location::getName));
        }

        // 5. 组装返回结果
        List<RegistrationLectureDTO> result = new ArrayList<>();
        for (Registration registration : registrations) {
            Lecture lecture = lectureMap.get(registration.getLectureId());
            if (lecture != null) {
                RegistrationLectureDTO dto = new RegistrationLectureDTO();
                dto.setId(registration.getId());
                dto.setLectureId(lecture.getId());
                dto.setLectureTitle(lecture.getTitle());
                // 设置分类名称 - 需要从分类服务获取
                dto.setCategoryName(getCategoryName(lecture.getCategoryId()));
                dto.setSpeaker(lecture.getSpeaker());
                // 通过locationId获取地点名称
                String locationName = "";
                if (lecture.getLocationId() != null) {
                    locationName = locationMap.get(lecture.getLocationId());
                }
                dto.setLocation(locationName != null ? locationName : "");
                // 使用lectureTime作为开始时间
                dto.setStartTime(lecture.getLectureTime());
                // 结束时间可以根据业务需求设置，例如讲座时间加上持续时间
                // 这里暂时也使用lectureTime
                dto.setEndTime(lecture.getLectureTime());
                dto.setDescription(lecture.getContent()); // 使用content作为描述
                dto.setRegisterTime(registration.getRegisterTime());
                dto.setCancelTime(registration.getCancelTime());
                dto.setStatus(registration.getStatus());
                dto.setPublishStatus(lecture.getPublishStatus()); // 设置讲座发布状态
                dto.setLectureStatus(lecture.getStatus()); // 设置讲座状态
                dto.setCheckinStatus(registration.getCheckinStatus() != null ? registration.getCheckinStatus() : 0); // 设置签到状态
                
                result.add(dto);
            }
        }
        
        return result;
    }
    
    /**
     * 根据分类ID获取分类名称
     * 
     * @param categoryId 分类ID
     * @return 分类名称
     */
    private String getCategoryName(Long categoryId) {
        if (categoryId == null) {
            return "";
        }
        
        LectureCategory category = lectureCategoryService.getById(categoryId);
        return category != null ? category.getCategoryName() : "";
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long registrationId) {
        // 1. 获取报名记录
        Registration registration = getById(registrationId);
        if (registration == null) {
            throw new ApiException("报名记录不存在");
        }
        
        // 2. 检查报名状态
        if (registration.getStatus() != 1) {
            throw new ApiException("只有已确认报名的学生才能签到");
        }
        
        // 3. 检查是否已签到
        if (registration.getCheckinStatus() != null && registration.getCheckinStatus() == 1) {
            throw new ApiException("您已经签到过了");
        }
        
        // 4. 获取讲座信息并检查讲座状态
        Lecture lecture = lectureService.getById(registration.getLectureId());
        if (lecture == null) {
            throw new ApiException("讲座不存在");
        }
        
        // 5. 检查讲座是否正在进行中
        if (lecture.getStatus() != 2) {
            if (lecture.getStatus() == 1) {
                throw new ApiException("讲座还未开始，无法签到");
            } else if (lecture.getStatus() == 3) {
                throw new ApiException("讲座已结束，无法签到");
            } else {
                throw new ApiException("当前讲座状态不允许签到");
            }
        }
        
        // 6. 更新签到状态与签到时间
        registration.setCheckinStatus(1);
        registration.setCheckinTime(LocalDateTime.now());
        boolean updateSuccess = updateById(registration);
        
        if (!updateSuccess) {
            throw new ApiException("签到失败，请稍后重试");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCheckin(Long registrationId) {
        // 1. 获取报名记录
        Registration registration = getById(registrationId);
        if (registration == null) {
            throw new ApiException("报名记录不存在");
        }
        
        // 2. 检查报名状态
        if (registration.getStatus() != 1) {
            throw new ApiException("只有已确认报名的记录才能取消签到");
        }
        
        // 3. 检查是否已签到
        if (registration.getCheckinStatus() == null || registration.getCheckinStatus() != 1) {
            throw new ApiException("您还未签到，无法取消签到");
        }
        
        // 4. 获取讲座信息并检查讲座状态
        Lecture lecture = lectureService.getById(registration.getLectureId());
        if (lecture == null) {
            throw new ApiException("讲座不存在");
        }
        
        // 5. 检查讲座是否正在进行中（只有进行中的讲座才能取消签到）
        if (lecture.getStatus() != 2) {
            throw new ApiException("只有正在进行中的讲座才能取消签到");
        }
        
        // 6. 更新签到状态为未签到，并清空签到时间
        boolean updateSuccess = update(new LambdaUpdateWrapper<Registration>()
                .eq(Registration::getId, registrationId)
                .set(Registration::getCheckinStatus, 0)
                .set(Registration::getCheckinTime, null));
        
        if (!updateSuccess) {
            throw new ApiException("取消签到失败，请稍后重试");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetCheckinStatusByLectureId(Long lectureId) {
        // 1. 检查讲座是否存在
        Lecture lecture = lectureService.getById(lectureId);
        if (lecture == null) {
            throw new ApiException("讲座不存在");
        }
        
        // 2. 查询该讲座的所有报名记录
        List<Registration> registrations = list(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getLectureId, lectureId)
                .eq(Registration::getStatus, 1)); // 只处理已确认的报名记录
        
        if (registrations.isEmpty()) {
             log.info("讲座[{}]没有需要重置的报名记录", lectureId);
             return;
         }
         
         // 3. 批量重置签到状态为未签到(0)，并清空签到时间
         int resetCount = 0;
         for (Registration registration : registrations) {
             if (registration.getCheckinStatus() != null && registration.getCheckinStatus() == 1) {
                 update(new LambdaUpdateWrapper<Registration>()
                         .eq(Registration::getId, registration.getId())
                         .set(Registration::getCheckinStatus, 0)
                         .set(Registration::getCheckinTime, null));
                 resetCount++;
             }
         }
         
         log.info("讲座[{}]已重置{}条报名记录的签到状态", lectureId, resetCount);
    }
}