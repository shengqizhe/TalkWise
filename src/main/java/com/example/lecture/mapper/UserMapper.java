package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM user WHERE username = #{username} AND deleted = 0")
    User selectByUsername(@Param("username") String username);
    
    /**
     * 根据邮箱查询用户
     */
    @Select("SELECT * FROM user WHERE email = #{email} AND deleted = 0")
    User selectByEmail(@Param("email") String email);
    
    /**
     * 删除用户兴趣标签关联
     */
    @Delete("DELETE FROM user_interest_tag WHERE user_id = #{userId}")
    int deleteUserInterestTags(Long userId);
    
    /**
     * 删除消息用户关联
     */
    @Delete("DELETE FROM message_user WHERE user_id = #{userId}")
    int deleteMessageUser(Long userId);
    
    /**
     * 删除用户评价
     */
    @Delete("DELETE FROM evaluation WHERE user_id = #{userId}")
    int deleteEvaluations(Long userId);
    
    /**
     * 删除用户互动记录
     */
    @Delete("DELETE FROM interaction WHERE question_user_id = #{userId} OR answer_user_id = #{userId}")
    int deleteInteractions(Long userId);
    
    /**
     * 删除用户签到记录
     */
    @Delete("DELETE FROM attendance WHERE user_id = #{userId}")
    int deleteAttendances(Long userId);
    
    /**
     * 删除用户报名记录
     */
    @Delete("DELETE FROM registration WHERE user_id = #{userId}")
    int deleteRegistrations(Long userId);
    
    /**
     * 删除用户推荐记录
     */
    @Delete("DELETE FROM recommendation_log WHERE user_id = #{userId}")
    int deleteRecommendationLogs(Long userId);
    
    /**
     * 更新讲座组织者ID为null
     */
    @Update("UPDATE lecture SET organizer_id = NULL WHERE organizer_id = #{userId}")
    int updateLectureOrganizerId(Long userId);
    
    /**
     * 更新消息发送者ID为null
     */
    @Update("UPDATE message SET sender_id = NULL WHERE sender_id = #{userId}")
    int updateMessageSenderId(Long userId);
    
    /**
     * 物理删除用户
     */
    @Delete("DELETE FROM user WHERE id = #{userId}")
    int deleteUserPhysically(Long userId);
}