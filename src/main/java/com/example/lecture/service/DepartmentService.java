package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.entity.Department;

import java.util.List;

/**
 * 系别Service接口
 */
public interface DepartmentService extends IService<Department> {
    
    /**
     * 获取所有系别列表
     */
    List<Department> getAllDepartments();
    
    /**
     * 根据关键字搜索系别
     * @param keyword 搜索关键字
     * @return 系别列表
     */
    List<Department> searchDepartments(String keyword);
    
    /**
     * 根据系别名称查询系别
     */
    Department getByDepartmentName(String departmentName);
    
    /**
     * 创建系别
     * @param department 系别信息
     * @return true表示创建新系别，false表示恢复已删除系别
     */
    boolean createDepartment(Department department);
    
    /**
     * 更新系别信息
     */
    void updateDepartment(Department department);
    
    /**
     * 删除系别
     */
    void deleteDepartment(Long id);
}