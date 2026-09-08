package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.entity.LectureCategory;
import com.example.lecture.mapper.LectureCategoryMapper;
import com.example.lecture.service.LectureCategoryService;
import org.springframework.stereotype.Service;

/**
 * 讲座分类Service实现类
 */
@Service
public class LectureCategoryServiceImpl extends ServiceImpl<LectureCategoryMapper, LectureCategory> implements LectureCategoryService {
}