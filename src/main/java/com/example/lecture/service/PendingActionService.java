package com.example.lecture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.lecture.dto.PendingActionResponse;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.PendingAction;

public interface PendingActionService extends IService<PendingAction> {
    PendingActionResponse prepareLectureCreation(Long userId, Lecture draft);

    /** 生成讲座修改草稿（仅白名单业务字段） */
    PendingActionResponse prepareLectureUpdate(Long userId, Long lectureId, Lecture patch);

    /** 生成讲座取消草稿 */
    PendingActionResponse prepareLectureCancel(Long userId, Long lectureId, String reason);

    /** 生成讲座发布/下架草稿 */
    PendingActionResponse prepareLecturePublish(Long userId, Long lectureId, Integer publishStatus);

    PendingActionResponse confirm(Long userId, Long actionId);

    PendingActionResponse reject(Long userId, Long actionId);
}
