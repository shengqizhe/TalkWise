package com.example.lecture.controller;

import com.example.lecture.common.api.Result;
import com.example.lecture.entity.SchoolProfile;
import com.example.lecture.service.SchoolProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学校画像查询控制器。
 */
@RestController
@RequestMapping("/api/school-profile")
public class SchoolProfileController {

    @Autowired
    private SchoolProfileService schoolProfileService;

    @GetMapping("/all")
    public Result<List<SchoolProfile>> getAllProfiles() {
        return Result.success(schoolProfileService.getAllProfiles());
    }

    @GetMapping("/school/{schoolName}")
    public Result<SchoolProfile> getBySchoolName(@PathVariable String schoolName) {
        return Result.success(schoolProfileService.getBySchoolName(schoolName));
    }
}
