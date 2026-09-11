package com.example.lecture.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Evaluation;
import com.example.lecture.dto.EvaluationDTO;
import com.example.lecture.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评价Controller
 */
@Tag(name = "评价管理", description = "评价相关接口")
@RestController
@RequestMapping("/evaluation")
public class EvaluationController {
    
    @Autowired
    private EvaluationService evaluationService;
    
    @Operation(summary = "分页查询评价列表")
    @GetMapping("/page")
    public Result<Page<EvaluationDTO>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long lectureId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        Page<Evaluation> page = new Page<>(current, size);
        return Result.success(evaluationService.getEvaluationPage(page, keyword, lectureId, teacherId, sort, order));
    }
    
    @Operation(summary = "获取讲座的评价列表")
    @GetMapping("/lecture/{lectureId}")
    public Result<List<EvaluationDTO>> getByLectureId(@PathVariable Long lectureId) {
        return Result.success(evaluationService.getEvaluationsByLectureId(lectureId));
    }
    
    @Operation(summary = "获取用户的评价列表")
    @GetMapping("/user/{userId}")
    public Result<List<EvaluationDTO>> getByUserId(@PathVariable Long userId) {
        return Result.success(evaluationService.getEvaluationsByUserId(userId));
    }
    
    @Operation(summary = "获取讲座的平均评分")
    @GetMapping("/lecture/{lectureId}/average-score")
    public Result<Double> getAverageScore(@PathVariable Long lectureId) {
        return Result.success(evaluationService.getAverageScoreByLectureId(lectureId));
    }
    
    @Operation(summary = "获取讲座的评价统计")
    @GetMapping("/lecture/{lectureId}/stats")
    public Result<EvaluationService.EvaluationStats> getStats(@PathVariable Long lectureId) {
        return Result.success(evaluationService.getEvaluationStatsByLectureId(lectureId));
    }

    @Operation(summary = "创建评价")
    @PostMapping("/create")
    public Result<Void> create(@RequestBody Evaluation evaluation) {
        evaluationService.createEvaluation(evaluation);
        return Result.success();
    }
    
    @Operation(summary = "更新评价")
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Evaluation evaluation) {
        evaluationService.updateEvaluation(evaluation);
        return Result.success();
    }
    
    @Operation(summary = "删除评价")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        evaluationService.deleteEvaluation(id);
        return Result.success();
    }
    
    @Operation(summary = "获取评价详情")
    @GetMapping("/{id}")
    public Result<Evaluation> getById(@PathVariable Long id) {
        return Result.success(evaluationService.getById(id));
    }
} 