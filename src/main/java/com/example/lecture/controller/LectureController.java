package com.example.lecture.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.RegistrationMapper;
import com.example.lecture.service.EvaluationService;
import com.example.lecture.service.LectureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Parameter;
import com.example.lecture.dto.PromotionContentDTO;
/**
 * 讲座Controller
 */
@Tag(name = "讲座管理", description = "讲座相关接口")
@RestController
@RequestMapping("/lecture")
public class LectureController {
    
    private final LectureService lectureService;
    private final RegistrationMapper registrationMapper;
    private final EvaluationService evaluationService;
    
    public LectureController(LectureService lectureService, RegistrationMapper registrationMapper, EvaluationService evaluationService) {
        this.lectureService = lectureService;
        this.registrationMapper = registrationMapper;
        this.evaluationService = evaluationService;
    }
    
    @Operation(summary = "分页查询讲座列表")
    @GetMapping("/page")
    public Result<Page<Lecture>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long organizerId,
            @RequestParam(required = false) Integer publishStatus,
            @RequestParam(required = false) Long lastUpdatedTimestamp,
            HttpServletRequest request) {
        Page<Lecture> page = new Page<>(current, size);
        Page<Lecture> result = lectureService.page(page, keyword, categoryId, organizerId, publishStatus);

        // 添加数据变化标志
        boolean dataChanged;
        if (lastUpdatedTimestamp != null) {
            // 检查数据是否有变化
            dataChanged = lectureService.hasDataChangedSince(result.getRecords(), lastUpdatedTimestamp);
        } else {
            dataChanged = true;
        }

        // 将数据变化标志添加到返回结果中
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            // 只需要在第一条记录中添加数据变化标志，减少不必要的数据传输
            Lecture firstLecture = result.getRecords().get(0);
            if (firstLecture.getExtendData() == null) {
                firstLecture.setExtendData(new HashMap<>());
            }
            firstLecture.getExtendData().put("dataChanged", dataChanged);

            // 如果没有数据变化，可以清空记录列表，减少数据传输量
            // 前端只需要检查dataChanged标志，不需要实际数据
            if (!dataChanged && "true".equals(request.getParameter("checkDataChange"))) {
                // 保留第一条记录（包含dataChanged标志）
                List<Lecture> reducedList = new ArrayList<>();
                reducedList.add(firstLecture);
                result.setRecords(reducedList);
                // 调整总记录数，避免前端分页错误
                result.setTotal(0);
            }
        }

        return Result.success(result);
    }
    
    @Operation(summary = "获取讲座详情")
    @GetMapping("/{id}")
    public Result<Lecture> getById(@PathVariable Long id) {
        return Result.success(lectureService.getById(id));
    }
    
    @Operation(summary = "发布讲座")
    @PostMapping("/publish")
    public Result<Void> publish(@RequestBody Lecture lecture) {
        lectureService.publish(lecture);
        return Result.success();
    }
    
    @Operation(summary = "更新讲座")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Lecture lecture) {
        lectureService.update(lecture);
        return Result.success();
    }
    
    @Operation(summary = "取消讲座")
    @PutMapping("/cancel/{id}")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam String reason) {
        lectureService.cancel(id, reason);
        return Result.success();
    }
    
    @Operation(summary = "更新讲座状态")
    @PutMapping("/status/{id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        lectureService.updateStatus(id, status);
        return Result.success();
    }
    
    @Operation(summary = "更新讲座发布状态")
    @PutMapping("/publish-status/{id}")
    public Result<Void> updatePublishStatus(@PathVariable Long id, @RequestParam Integer publishStatus) {
        lectureService.updatePublishStatus(id, publishStatus);
        return Result.success();
    }
    
    @Operation(summary = "获取教师讲座统计信息")
    @GetMapping("/stats/{teacherId}")
    public Result<Map<String, Object>> getLectureStats(@PathVariable Long teacherId) {
        // 查询该教师的所有讲座
        LambdaQueryWrapper<Lecture> wrapper = new LambdaQueryWrapper<Lecture>()
                .eq(Lecture::getOrganizerId, teacherId)
                .eq(Lecture::getDeleted, 0);
        List<Lecture> lectures = lectureService.list(wrapper);
        
        // 统计数据
        int totalLectures = lectures.size();
        // 从报名表中获取总报名人数
        int totalRegistrations = registrationMapper.countRegistrationsByTeacherId(teacherId);
        int upcomingLectures = (int) lectures.stream().filter(l -> l.getStatus() == 1).count();
        
        // 计算平均评分 - 只计算该教师的讲座评价
        double avgRating = 0.0;
        if (!lectures.isEmpty()) {
            // 获取该教师所有讲座的ID
            List<Long> lectureIds = lectures.stream()
                    .map(Lecture::getId)
                    .collect(Collectors.toList());
            
            // 查询这些讲座的所有评价
            LambdaQueryWrapper<Evaluation> evaluationWrapper = new LambdaQueryWrapper<Evaluation>()
                    .in(Evaluation::getLectureId, lectureIds)
                    .select(Evaluation::getScore);
            
            List<Evaluation> evaluations = evaluationService.list(evaluationWrapper);
            
            if (!evaluations.isEmpty()) {
                double totalScore = evaluations.stream()
                        .mapToDouble(e -> e.getScore().doubleValue())
                        .sum();
                avgRating = totalScore / evaluations.size();
            }
        }
        
        // 构建返回结果
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLectures", totalLectures);
        stats.put("totalRegistrations", totalRegistrations);
        stats.put("upcomingLectures", upcomingLectures);
        stats.put("avgRating", Math.round(avgRating * 10.0) / 10.0); // 保留一位小数
        
        return Result.success(stats);
    }
    
    @Operation(summary = "学生端分页查询讲座列表")
    @GetMapping("/student/page")
    public Result<Page<Lecture>> studentPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long lastUpdatedTimestamp,
            HttpServletRequest request) {
        Page<Lecture> page = new Page<>(current, size);
        // 强制设置publishStatus=1，只返回已发布的讲座
        // 如果未指定状态，默认返回所有状态的讲座
        Page<Lecture> result = lectureService.page(page, keyword, categoryId, null, 1, status);

        // 添加数据变化标志
        boolean dataChanged;
        if (lastUpdatedTimestamp != null) {
            // 检查数据是否有变化
            dataChanged = lectureService.hasDataChangedSince(result.getRecords(), lastUpdatedTimestamp);
        } else {
            dataChanged = true;
        }

        // 将数据变化标志添加到返回结果中
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            // 只需要在第一条记录中添加数据变化标志，减少不必要的数据传输
            Lecture firstLecture = result.getRecords().get(0);
            if (firstLecture.getExtendData() == null) {
                firstLecture.setExtendData(new HashMap<>());
            }
            firstLecture.getExtendData().put("dataChanged", dataChanged);

            // 如果没有数据变化，可以清空记录列表，减少数据传输量
            // 前端只需要检查dataChanged标志，不需要实际数据
            if (!dataChanged && "true".equals(request.getParameter("checkDataChange"))) {
                // 保留第一条记录（包含dataChanged标志）
                List<Lecture> reducedList = new ArrayList<>();
                reducedList.add(firstLecture);
                result.setRecords(reducedList);
                // 调整总记录数，避免前端分页错误
                result.setTotal(0);
            }
        }

        return Result.success(result);
    }
    
    @Operation(summary = "更新讲座宣讲图片")
    @PutMapping("/promotion-content/{lectureId}")
    public Result<Void> updatePromotionContent(
            @Parameter(description = "讲座ID") @PathVariable Long lectureId,
            @Parameter(description = "宣讲图片") @RequestBody PromotionContentDTO promotionContent) {
        lectureService.updatePromotionContent(lectureId, promotionContent);
        return Result.success();
    }
    
    @Operation(summary = "获取讲座宣讲图片")
    @GetMapping("/promotion-content/{lectureId}")
    public Result<PromotionContentDTO> getPromotionContent(
            @Parameter(description = "讲座ID") @PathVariable Long lectureId) {
        PromotionContentDTO promotionContent = lectureService.getPromotionContent(lectureId);
        return Result.success(promotionContent);
    }
    
    @Operation(summary = "删除讲座宣讲图片")
    @DeleteMapping("/promotion-content/{lectureId}")
    public Result<Void> deletePromotionContent(
            @Parameter(description = "讲座ID") @PathVariable Long lectureId) {
        lectureService.deletePromotionContent(lectureId);
        return Result.success();
    }
}