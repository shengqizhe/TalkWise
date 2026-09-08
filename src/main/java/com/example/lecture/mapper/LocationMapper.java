package com.example.lecture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lecture.entity.Location;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 地点Mapper接口
 */
@Mapper
public interface LocationMapper extends BaseMapper<Location> {
    
    /**
     * 根据地点名称查询地点
     */
    @Select("SELECT * FROM location WHERE name = #{name}")
    Location selectByName(@Param("name") String name);
    
    /**
     * 根据地点类型查询地点列表
     */
    @Select("SELECT * FROM location WHERE type = #{type} ORDER BY name")
    List<Location> selectByType(@Param("type") String type);
    
    /**
     * 根据经纬度范围查询地点列表
     */
    @Select("SELECT * FROM location WHERE longitude BETWEEN #{minLongitude} AND #{maxLongitude} " +
            "AND latitude BETWEEN #{minLatitude} AND #{maxLatitude}")
    List<Location> selectByCoordinateRange(@Param("minLongitude") Double minLongitude,
                                          @Param("maxLongitude") Double maxLongitude,
                                          @Param("minLatitude") Double minLatitude,
                                          @Param("maxLatitude") Double maxLatitude);
}