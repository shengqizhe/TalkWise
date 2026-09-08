package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.LectureCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 讲座类别Mapper接口
 */
@Mapper
public interface LectureCategoryMapper extends BaseMapper<LectureCategory> {
} 
