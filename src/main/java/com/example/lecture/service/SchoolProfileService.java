package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.entity.SchoolProfile;

import java.util.List;

/**
 * 学校画像Service接口。
 */
public interface SchoolProfileService extends IService<SchoolProfile> {

    List<SchoolProfile> getAllProfiles();

    SchoolProfile getBySchoolName(String schoolName);

    /**
     * 根据地点表中的容量重算学校画像。
     */
    void recalculateRoomCapacity(String schoolName);
}
