package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.common.api.ResultCode;
import com.example.lecture.entity.Location;
import com.example.lecture.entity.Lecture;
import com.example.lecture.mapper.LocationMapper;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 地点Service实现类
 */
@Service
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements LocationService {
    
    @Autowired
    private LectureMapper lectureMapper;
    
    @Override
    public List<Location> getAllLocations() {
        return list(new LambdaQueryWrapper<Location>()
                .orderByAsc(Location::getName));
    }
    
    @Override
    public Location getByName(String name) {
        return baseMapper.selectByName(name);
    }
    
    @Override
    public List<Location> getByType(String type) {
        return baseMapper.selectByType(type);
    }
    
    @Override
    public List<Location> getByCoordinateRange(Double minLongitude, Double maxLongitude, 
                                              Double minLatitude, Double maxLatitude) {
        return baseMapper.selectByCoordinateRange(minLongitude, maxLongitude, minLatitude, maxLatitude);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createLocation(Location location) {
        // 检查地点名称是否已存在
        if (getByName(location.getName()) != null) {
            throw new ApiException(ResultCode.DUPLICATE_LOCATION_NAME);
        }
        
        // 保存地点
        save(location);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLocation(Location location) {
        // 检查地点是否存在
        if (getById(location.getId()) == null) {
            throw new ApiException(ResultCode.LOCATION_NOT_EXIST);
        }
        
        // 检查地点名称是否与其他地点重复
        Location existingLocation = getByName(location.getName());
        if (existingLocation != null && !existingLocation.getId().equals(location.getId())) {
            throw new ApiException(ResultCode.DUPLICATE_LOCATION_NAME);
        }
        
        // 更新地点信息
        updateById(location);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLocation(Long id) {
        // 检查地点是否存在
        if (getById(id) == null) {
            throw new ApiException(ResultCode.LOCATION_NOT_EXIST);
        }
        
        // 检查地点是否被讲座使用
        if (isLocationUsedByLecture(id)) {
            throw new ApiException(ResultCode.LOCATION_IN_USE);
        }
        
        // 删除地点（逻辑删除）
        removeById(id);
    }
    
    @Override
    public boolean isLocationUsedByLecture(Long locationId) {
        LambdaQueryWrapper<Lecture> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Lecture::getLocationId, locationId)
                   .eq(Lecture::getDeleted, 0);
        return lectureMapper.selectCount(queryWrapper) > 0;
    }
}