package com.example.lecture.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.dto.PendingActionResponse;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.PendingAction;
import com.example.lecture.mapper.PendingActionMapper;
import com.example.lecture.service.LectureCategoryService;
import com.example.lecture.service.LectureService;
import com.example.lecture.service.LocationService;
import com.example.lecture.service.PendingActionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PendingActionServiceImpl extends ServiceImpl<PendingActionMapper, PendingAction>
        implements PendingActionService {
    private final LectureService lectureService;
    private final LectureCategoryService categoryService;
    private final LocationService locationService;
    private final AgentRoleHelper roleHelper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingActionResponse prepareLectureCreation(Long userId, Lecture draft) {
        requireCreator(userId);
        validateDraft(draft, userId);
        try {
            PendingAction action = new PendingAction();
            action.setUserId(userId);
            action.setType(PendingAction.TYPE_CREATE_LECTURE);
            action.setPayload(objectMapper.writeValueAsString(draft));
            action.setStatus(PendingAction.STATUS_PENDING);
            save(action);
            return response(action, draft);
        } catch (Exception e) {
            throw new ApiException("无法保存讲座草稿", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingActionResponse confirm(Long userId, Long actionId) {
        PendingAction action = ownedAction(userId, actionId);
        if (!PendingAction.STATUS_PENDING.equals(action.getStatus())) {
            throw new ApiException("该确认动作已处理，不能重复确认");
        }
        Lecture draft = payload(action);
        validateDraft(draft, userId);
        // 条件更新作为并发闸门：只有一个请求可以从 PENDING 抢占到 CONFIRMED。
        int claimed = baseMapper.update(null, new LambdaUpdateWrapper<PendingAction>()
                .eq(PendingAction::getId, actionId)
                .eq(PendingAction::getUserId, userId)
                .eq(PendingAction::getStatus, PendingAction.STATUS_PENDING)
                .set(PendingAction::getStatus, PendingAction.STATUS_CONFIRMED));
        if (claimed != 1) {
            throw new ApiException("该确认动作已处理，不能重复确认");
        }
        draft.setOrganizerId(userId);
        lectureService.publish(draft);
        action.setStatus(PendingAction.STATUS_CONFIRMED);
        action.setResultId(draft.getId());
        updateById(action);
        return response(action, draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingActionResponse reject(Long userId, Long actionId) {
        PendingAction action = ownedAction(userId, actionId);
        int changed = baseMapper.update(null, new LambdaUpdateWrapper<PendingAction>()
                .eq(PendingAction::getId, actionId).eq(PendingAction::getUserId, userId)
                .eq(PendingAction::getStatus, PendingAction.STATUS_PENDING)
                .set(PendingAction::getStatus, PendingAction.STATUS_REJECTED));
        if (changed != 1) throw new ApiException("该确认动作已处理，不能重复拒绝");
        action.setStatus(PendingAction.STATUS_REJECTED);
        return response(action, payload(action));
    }

    private void requireCreator(Long userId) {
        if (userId == null || !(roleHelper.isAdmin(userId) || roleHelper.isTeacher(userId)))
            throw new ApiException("只有教师或管理员可以创建讲座");
    }

    private PendingAction ownedAction(Long userId, Long id) {
        if (userId == null || id == null) throw new ApiException("参数不能为空");
        PendingAction action = getById(id);
        if (action == null || !userId.equals(action.getUserId())) throw new ApiException("确认动作不存在");
        return action;
    }

    private void validateDraft(Lecture d, Long userId) {
        if (d == null || !StringUtils.hasText(d.getTitle()) || !StringUtils.hasText(d.getSummary())
                || !StringUtils.hasText(d.getContent()) || !StringUtils.hasText(d.getSpeaker())
                || d.getCategoryId() == null || d.getLectureTime() == null || d.getCapacity() == null
                || d.getCapacity() <= 0) throw new ApiException("讲座标题、摘要、内容、主讲人、类别、时间和正容量不能为空");
        if (categoryService.getById(d.getCategoryId()) == null) throw new ApiException("讲座类别不存在");
        if (d.getLectureTime().isBefore(LocalDateTime.now())) throw new ApiException("讲座时间必须晚于当前时间");
        if (d.getLocationId() != null) {
            var location = locationService.getById(d.getLocationId());
            if (location == null) throw new ApiException("讲座地点不存在");
            if (location.getCapacity() != null && d.getCapacity() > location.getCapacity()) throw new ApiException("讲座容量不能超过地点容量");
        }
        if (d.getOrganizerId() != null && !userId.equals(d.getOrganizerId())) throw new ApiException("不能代替其他用户创建讲座");
        d.setOrganizerId(userId);
    }

    private Lecture payload(PendingAction action) {
        try { return objectMapper.readValue(action.getPayload(), Lecture.class); }
        catch (Exception e) { throw new ApiException("讲座草稿数据无效", e); }
    }

    private PendingActionResponse response(PendingAction a, Lecture d) {
        return PendingActionResponse.of(a.getId(), a.getType(), a.getStatus(), d, a.getResultId());
    }
}
