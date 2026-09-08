package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.entity.LectureCategory;
import com.example.lecture.service.LectureCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 讲座分类Controller
 */
@Tag(name = "讲座分类管理", description = "讲座分类相关接口")
@RestController
@RequestMapping("/lecture/category")
public class LectureCategoryController {
    
    @Autowired
    private LectureCategoryService lectureCategoryService;
    
    @Operation(summary = "获取所有讲座分类")
    @GetMapping("/list")
    public Result<List<LectureCategory>> list() {
        List<LectureCategory> categories = lectureCategoryService.list();
        return Result.success(categories);
    }
}