package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.Department;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系别Mapper接口
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
} 