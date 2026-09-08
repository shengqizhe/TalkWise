package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Department;
import com.example.lecture.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系别Controller
 */
@Tag(name = "系别管理", description = "系别相关接口")
@RestController
@RequestMapping("/department")
public class DepartmentController {
    
    private final DepartmentService departmentService;
    
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }
    
    @Operation(summary = "获取所有系别")
    @GetMapping("/list")
    public Result<List<Department>> getAllDepartments() {
        List<Department> departments = departmentService.getAllDepartments();
        return Result.success(departments);
    }
    
    @Operation(summary = "搜索系别")
    @GetMapping("/search")
    public Result<List<Department>> searchDepartments(@RequestParam(required = false) String keyword) {
        List<Department> departments = departmentService.searchDepartments(keyword);
        return Result.success(departments);
    }
    
    @Operation(summary = "根据ID获取系别")
    @GetMapping("/{id}")
    public Result<Department> getDepartmentById(@PathVariable Long id) {
        Department department = departmentService.getById(id);
        return Result.success(department);
    }
    
    @Operation(summary = "创建系别")
    @PostMapping("/create")
    public Result<Void> createDepartment(@RequestBody Department department) {
        boolean isNewDepartment = departmentService.createDepartment(department);
        if (isNewDepartment) {
            return Result.success(null, "创建系别成功");
        } else {
            return Result.success(null, "系别已恢复");
        }
    }
    
    @Operation(summary = "更新系别")
    @PutMapping("/update")
    public Result<Void> updateDepartment(@RequestBody Department department) {
        departmentService.updateDepartment(department);
        return Result.success();
    }
    
    @Operation(summary = "删除系别")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return Result.success();
    }
}