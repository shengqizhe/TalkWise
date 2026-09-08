package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.common.api.ResultCode;
import com.example.lecture.entity.Department;
import com.example.lecture.mapper.DepartmentMapper;
import com.example.lecture.service.DepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系别Service实现类
 */
@Service
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {
    
    @Override
    public List<Department> getAllDepartments() {
        return list(new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getId));
    }
    
    @Override
    public List<Department> searchDepartments(String keyword) {
        LambdaQueryWrapper<Department> queryWrapper = new LambdaQueryWrapper<>();
        
        // 如果关键字不为空，则添加模糊查询条件
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like(Department::getDepartmentName, keyword)
                    .or()
                    .like(Department::getDescription, keyword);
        }
        
        // 按ID升序排序
        queryWrapper.orderByAsc(Department::getId);
        
        return list(queryWrapper);
    }
    
    @Override
    public Department getByDepartmentName(String departmentName) {
        return getOne(new LambdaQueryWrapper<Department>()
                .eq(Department::getDepartmentName, departmentName));
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createDepartment(Department department) {
        // 检查系别名称是否已存在
        Department existingDepartment = getByDepartmentName(department.getDepartmentName());
        
        if (existingDepartment != null) {
            // 如果系别已存在，检查是否为逻辑删除状态
            if (existingDepartment.getDeleted() == 1) {
                // 如果是已删除状态，则恢复（设置deleted为0）
                existingDepartment.setDeleted(0);
                updateById(existingDepartment);
                return false; // 返回false表示恢复已删除系别
            } else {
                // 如果不是已删除状态，则提示系别名称重复
                throw new ApiException(ResultCode.DUPLICATE_DEPARTMENT_NAME);
            }
        }
        
        // 保存新系别
        save(department);
        return true; // 返回true表示创建新系别
    }

    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDepartment(Department department) {
        // 检查系别是否存在
        if (getById(department.getId()) == null) {
            throw new ApiException(ResultCode.DEPARTMENT_NOT_EXIST);
        }
        
        // 检查系别名称是否与其他系别重复
        Department existingDepartment = getByDepartmentName(department.getDepartmentName());
        if (existingDepartment != null && !existingDepartment.getId().equals(department.getId())) {
            throw new ApiException(ResultCode.DUPLICATE_DEPARTMENT_NAME);
        }
        
        // 更新系别信息
        updateById(department);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartment(Long id) {
        // 检查系别是否存在
        if (getById(id) == null) {
            throw new ApiException(ResultCode.DEPARTMENT_NOT_EXIST);
        }
        
        // 删除系别（逻辑删除）
        removeById(id);
    }
}