package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.Lecture;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
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
     * 根据关键词模糊查询讲座：标题、摘要、内容、关键词，以及所属分类名称。
     *
     * <p>分类名参与匹配是必要的：用户常按"软件工程""人工智能"这类学科方向找讲座，
     * 而这些词往往只存在于 lecture_category.category_name，不在讲座正文里。</p>
     */
    @Select("SELECT l.* FROM lecture l LEFT JOIN lecture_category c ON l.category_id = c.id "
            + "WHERE (l.title LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.summary LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.content LIKE CONCAT('%', #{keyword}, '%') "
            + "OR l.keywords LIKE CONCAT('%', #{keyword}, '%') "
            + "OR c.category_name LIKE CONCAT('%', #{keyword}, '%')) "
            + "AND l.deleted = 0 AND l.publish_status = 1")
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

    /**
     * 查询指定教室在 [newStart, newEnd) 区间内已占用的讲座（教室推荐的时间冲突判定）。
     *
     * <p>重叠判定：lecture_time &lt; newEnd AND lecture_time + duration_minutes &gt; newStart；
     * 仅统计未删除且未取消（status &lt;&gt; 4）的记录；excludeLectureId 非空时排除自身（改期场景）。</p>
     */
    @Select("<script>"
            + "SELECT * FROM lecture WHERE location_id = #{locationId} AND deleted = 0 AND status &lt;&gt; 4 "
            + "AND lecture_time &lt; #{newEnd} "
            + "AND DATE_ADD(lecture_time, INTERVAL duration_minutes MINUTE) &gt; #{newStart} "
            + "<if test='excludeLectureId != null'>AND id &lt;&gt; #{excludeLectureId} </if>"
            + "</script>")
    List<Lecture> selectConflictingLectures(@Param("locationId") Long locationId,
                                            @Param("newStart") LocalDateTime newStart,
                                            @Param("newEnd") LocalDateTime newEnd,
                                            @Param("excludeLectureId") Long excludeLectureId);
} 