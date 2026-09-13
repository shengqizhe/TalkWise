package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.entity.Location;
import com.example.lecture.entity.SchoolProfile;
import com.example.lecture.mapper.LocationMapper;
import com.example.lecture.mapper.SchoolProfileMapper;
import com.example.lecture.service.SchoolProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学校画像Service实现类。
 */
@Service
public class SchoolProfileServiceImpl extends ServiceImpl<SchoolProfileMapper, SchoolProfile>
        implements SchoolProfileService {

    @Autowired
    private LocationMapper locationMapper;

    @Override
    public List<SchoolProfile> getAllProfiles() {
        return list(new LambdaQueryWrapper<SchoolProfile>().orderByAsc(SchoolProfile::getSchoolName));
    }

    @Override
    public SchoolProfile getBySchoolName(String schoolName) {
        return getOne(new LambdaQueryWrapper<SchoolProfile>()
                .eq(SchoolProfile::getSchoolName, schoolName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateRoomCapacity(String schoolName) {
        if (schoolName == null || schoolName.isBlank()) {
            return;
        }

        List<Location> locations = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                .eq(Location::getSchoolName, schoolName)
                .gt(Location::getCapacity, 0)
                .orderByAsc(Location::getCapacity));
        SchoolProfile profile = getBySchoolName(schoolName);
        if (profile == null) {
            profile = new SchoolProfile();
            profile.setSchoolName(schoolName);
        }
        if (locations.isEmpty()) {
            profile.setMinRoomCapacity(0);
            profile.setMaxRoomCapacity(0);
        } else {
            profile.setMinRoomCapacity(locations.get(0).getCapacity());
            profile.setMaxRoomCapacity(locations.get(locations.size() - 1).getCapacity());
        }
        saveOrUpdate(profile);
    }
}
