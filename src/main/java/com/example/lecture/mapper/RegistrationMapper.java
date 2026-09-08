package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 报名记录Mapper接口
 */
@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {
    
    /**
     * 查询用户是否已报名讲座
     */
    @Select("SELECT COUNT(*) FROM registration WHERE user_id = #{userId} AND lecture_id = #{lectureId} AND status = 1 AND deleted = 0")
    int countByUserIdAndLectureId(Long userId, Long lectureId);
    
    /**
     * 统计教师所有讲座的有效报名人数
     */
    @Select("SELECT COUNT(*) FROM registration r JOIN lecture l ON r.lecture_id = l.id WHERE l.organizer_id = #{teacherId} AND r.status = 1 AND r.deleted = 0 AND l.deleted = 0")
    int countRegistrationsByTeacherId(Long teacherId);
}