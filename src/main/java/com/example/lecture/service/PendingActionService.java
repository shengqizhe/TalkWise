package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.dto.PendingActionResponse;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.PendingAction;

public interface PendingActionService extends IService<PendingAction> {
    PendingActionResponse prepareLectureCreation(Long userId, Lecture draft);
    PendingActionResponse confirm(Long userId, Long actionId);
    PendingActionResponse reject(Long userId, Long actionId);
}
