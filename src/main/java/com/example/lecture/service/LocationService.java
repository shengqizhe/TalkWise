package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.entity.Location;

import java.util.List;

/**
 * 地点Service接口
 */
public interface LocationService extends IService<Location> {
    
    /**
     * 获取所有地点列表
     */
    List<Location> getAllLocations();
    
    /**
     * 根据地点名称查询地点
     */
    Location getByName(String name);
    
    /**
     * 根据地点类型查询地点列表
     */
    List<Location> getByType(String type);
    
    /**
     * 根据经纬度范围查询地点列表
     */
    List<Location> getByCoordinateRange(Double minLongitude, Double maxLongitude, 
                                       Double minLatitude, Double maxLatitude);
    
    /**
     * 创建地点
     */
    void createLocation(Location location);
    
    /**
     * 更新地点信息
     */
    void updateLocation(Location location);
    
    /**
     * 删除地点
     */
    void deleteLocation(Long id);
    
    /**
     * 检查地点是否被讲座使用
     */
    boolean isLocationUsedByLecture(Long locationId);
}