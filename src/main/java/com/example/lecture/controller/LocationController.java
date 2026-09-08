package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.entity.Location;
import com.example.lecture.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地点控制器
 */
@RestController
@RequestMapping("/api/location")
public class LocationController {

    @Autowired
    private LocationService locationService;

    /**
     * 获取所有地点列表
     */
    @GetMapping("/all")
    public Result<List<Location>> getAllLocations() {
        List<Location> locations = locationService.getAllLocations();
        return Result.success(locations);
    }

    /**
     * 根据名称查询地点
     */
    @GetMapping("/name/{name}")
    public Result<Location> getLocationByName(@PathVariable String name) {
        Location location = locationService.getByName(name);
        return Result.success(location);
    }

    /**
     * 根据类型查询地点
     */
    @GetMapping("/type/{type}")
    public Result<List<Location>> getLocationByType(@PathVariable String type) {
        List<Location> locations = locationService.getByType(type);
        return Result.success(locations);
    }

    /**
     * 创建地点
     */
    @PostMapping("/create")
    public Result<Void> createLocation(@RequestBody Location location) {
        locationService.createLocation(location);
        return Result.success();
    }

    /**
     * 更新地点
     */
    @PutMapping("/update")
    public Result<Void> updateLocation(@RequestBody Location location) {
        locationService.updateLocation(location);
        return Result.success();
    }

    /**
     * 删除地点
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteLocation(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return Result.success();
    }
}