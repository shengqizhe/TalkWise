package com.example.lecture.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.lecture.common.api.Result;
import com.example.lecture.entity.SchoolProfile;
import com.example.lecture.service.SchoolProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学校画像查询控制器。
 */
@RestController
@RequestMapping("/school-profile")
public class SchoolProfileController {

    @Autowired
    private SchoolProfileService schoolProfileService;

    @PostMapping
    @SaCheckRole("admin")
    public Result<SchoolProfile> create(@RequestBody SchoolProfile profile) {
        if (profile == null || profile.getSchoolName() == null || profile.getSchoolName().isBlank()) {
            return Result.failed("学校名称不能为空");
        }
        profile.setId(null);
        profile.setMinRoomCapacity(0);
        profile.setMaxRoomCapacity(0);
        schoolProfileService.save(profile);
        return Result.success(profile);
    }

    @PutMapping("/{id}")
    @SaCheckRole("admin")
    public Result<SchoolProfile> update(@PathVariable Long id, @RequestBody SchoolProfile profile) {
        SchoolProfile existing = schoolProfileService.getById(id);
        if (existing == null) return Result.failed("学校画像不存在");
        if (profile == null || profile.getSchoolName() == null || profile.getSchoolName().isBlank()) {
            return Result.failed("学校名称不能为空");
        }
        existing.setSchoolName(profile.getSchoolName().trim());
        existing.setProfileContent(profile.getProfileContent());
        schoolProfileService.updateById(existing);
        return Result.success(existing);
    }

    @DeleteMapping("/{id}")
    @SaCheckRole("admin")
    public Result<Void> delete(@PathVariable Long id) {
        if (!schoolProfileService.removeById(id)) return Result.failed("学校画像不存在");
        return Result.success();
    }
    @GetMapping("/all")
    public Result<List<SchoolProfile>> getAllProfiles() {
        return Result.success(schoolProfileService.getAllProfiles());
    }

    @GetMapping("/school/{schoolName}")
    public Result<SchoolProfile> getBySchoolName(@PathVariable String schoolName) {
        return Result.success(schoolProfileService.getBySchoolName(schoolName));
    }
}
