package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.Lecture;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 讲座Mapper接口
 */
@Mapper
public interface LectureMapper extends BaseMapper<Lecture> {
    
    /**
     * 更新报名人数
     */
    @Update("UPDATE lecture SET registered_count = registered_count + #{count} WHERE id = #{lectureId}")
    int updateRegisteredCount(@Param("lectureId") Long lectureId, @Param("count") int count);
    
    /**
     * 更新讲座状态
     */
    @Update("UPDATE lecture SET status = #{status} WHERE id = #{lectureId}")
    int updateStatus(@Param("lectureId") Long lectureId, @Param("status") Integer status);
    
    /**
     * 更新讲座发布状态
     */
    @Update("UPDATE lecture SET publish_status = #{publishStatus} WHERE id = #{lectureId}")
    int updatePublishStatus(@Param("lectureId") Long lectureId, @Param("publishStatus") Integer publishStatus);

    /**
     * 根据关键词模糊查询讲座（标题、摘要、内容、关键词）
     */
    @Select("SELECT * FROM lecture WHERE (title LIKE CONCAT('%', #{keyword}, '%') OR summary LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%') OR keywords LIKE CONCAT('%', #{keyword}, '%')) AND deleted = 0 AND publish_status = 1")
    List<Lecture> selectByKeyword(@Param("keyword") String keyword);

    /**
     * 根据星期几和时间段查询讲座
     */
    @Select("SELECT * FROM lecture WHERE DAYOFWEEK(lecture_time) = #{dayOfWeek} AND ((#{timeSlot} = '上午' AND HOUR(lecture_time) >= 8 AND HOUR(lecture_time) < 12) OR (#{timeSlot} = '下午' AND HOUR(lecture_time) >= 12 AND HOUR(lecture_time) < 18) OR (#{timeSlot} = '晚上' AND HOUR(lecture_time) >= 18 AND HOUR(lecture_time) < 23)) AND deleted = 0 AND publish_status = 1")
    List<Lecture> selectByDayOfWeek(@Param("dayOfWeek") int dayOfWeek, @Param("timeSlot") String timeSlot);

    /**
     * 查询所有已发布讲座
     */
    @Select("SELECT * FROM lecture WHERE deleted=0 AND publish_status=1")
    List<Lecture> selectAllPublished();

    /**
     * 根据讲师名字模糊查询讲座
     */
    @Select("SELECT * FROM lecture WHERE speaker LIKE CONCAT('%', #{speaker}, '%') AND deleted = 0 AND publish_status = 1")
    List<Lecture> selectBySpeaker(@Param("speaker") String speaker);
} 