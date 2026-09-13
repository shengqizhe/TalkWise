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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 待确认写动作：统一的「草稿 → 明确确认 → 执行」闭环。
 * 创建/修改/取消/发布四类讲座动作共用同一张表与同一套并发闸门。
 */
@Service
@RequiredArgsConstructor
public class PendingActionServiceImpl extends ServiceImpl<PendingActionMapper, PendingAction>
        implements PendingActionService {

    /** 取消原因在草稿 JSON 中的附加字段名（不属于 Lecture 实体） */
    private static final String REASON_FIELD = "_reason";

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
        return saveAction(userId, PendingAction.TYPE_CREATE_LECTURE, draft, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingActionResponse prepareLectureUpdate(Long userId, Long lectureId, Lecture patch) {
        requireCreator(userId);
        Lecture existing = requireOwnedLecture(lectureId, userId);
        if (existing.getStatus() == 3 || existing.getStatus() == 4) {
            throw new ApiException("讲座已结束或已取消，无法修改");
        }
        if (patch == null || !hasUpdatableField(patch)) {
            throw new ApiException("没有需要修改的内容");
        }
        if (patch.getLectureTime() != null && patch.getLectureTime().isBefore(LocalDateTime.now())) {
            throw new ApiException("讲座时间必须晚于当前时间");
        }
        if (patch.getCapacity() != null && patch.getCapacity() <= 0) {
            throw new ApiException("讲座容量必须为正数");
        }
        if (patch.getDurationMinutes() != null && patch.getDurationMinutes() <= 0) {
            throw new ApiException("讲座时长必须为正数");
        }
        if (patch.getCategoryId() != null && categoryService.getById(patch.getCategoryId()) == null) {
            throw new ApiException("讲座类别不存在");
        }
        // 草稿只保留 id 与白名单字段，禁止携带服务端控制字段
        Lecture draft = new Lecture();
        draft.setId(lectureId);
        draft.setTitle(patch.getTitle());
        draft.setSummary(patch.getSummary());
        draft.setContent(patch.getContent());
        draft.setSpeaker(patch.getSpeaker());
        draft.setCategoryId(patch.getCategoryId());
        draft.setLocationId(patch.getLocationId());
        draft.setLectureTime(patch.getLectureTime());
        draft.setDurationMinutes(patch.getDurationMinutes());
        draft.setCapacity(patch.getCapacity());
        return saveAction(userId, PendingAction.TYPE_UPDATE_LECTURE, draft, lectureId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingActionResponse prepareLectureCancel(Long userId, Long lectureId, String reason) {
        requireCreator(userId);
        Lecture existing = requireOwnedLecture(lectureId, userId);
        if (existing.getStatus() == 3 || existing.getStatus() == 4) {
            throw new ApiException("讲座已结束或已取消，无需重复取消");
        }
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "用户取消";
        Lecture draft = new Lecture();
        draft.setId(lectureId);
        draft.setTitle(existing.getTitle());
        draft.setLectureTime(existing.getLectureTime());
        draft.setStatus(existing.getStatus());
        return saveAction(userId, PendingAction.TYPE_CANCEL_LECTURE, draft, lectureId, normalizedReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingActionResponse prepareLecturePublish(Long userId, Long lectureId, Integer publishStatus) {
        requireCreator(userId);
        Lecture existing = requireOwnedLecture(lectureId, userId);
        if (publishStatus == null || (publishStatus != 0 && publishStatus != 1)) {
            throw new ApiException("无效的发布状态");
        }
        if (existing.getStatus() == 3) {
            throw new ApiException("已结束的讲座不能变更发布状态");
        }
        Lecture draft = new Lecture();
        draft.setId(lectureId);
        draft.setTitle(existing.getTitle());
        draft.setLectureTime(existing.getLectureTime());
        draft.setPublishStatus(publishStatus);
        return saveAction(userId, PendingAction.TYPE_PUBLISH_LECTURE, draft, lectureId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingActionResponse confirm(Long userId, Long actionId) {
        PendingAction action = ownedAction(userId, actionId);
        if (!PendingAction.STATUS_PENDING.equals(action.getStatus())) {
            throw new ApiException("该确认动作已处理，不能重复确认");
        }
        Lecture draft = payload(action);
        boolean admin = roleHelper.isAdmin(userId);
        // 条件更新作为并发闸门：只有一个请求可以从 PENDING 抢占到 CONFIRMED。
        int claimed = baseMapper.update(null, new LambdaUpdateWrapper<PendingAction>()
                .eq(PendingAction::getId, actionId)
                .eq(PendingAction::getUserId, userId)
                .eq(PendingAction::getStatus, PendingAction.STATUS_PENDING)
                .set(PendingAction::getStatus, PendingAction.STATUS_CONFIRMED));
        if (claimed != 1) {
            throw new ApiException("该确认动作已处理，不能重复确认");
        }
        Long resultId = dispatch(action, draft, userId, admin);
        action.setStatus(PendingAction.STATUS_CONFIRMED);
        action.setResultId(resultId);
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

    /** 按动作类型分派到已带归属校验的业务方法；返回动作产生的讲座ID */
    private Long dispatch(PendingAction action, Lecture draft, Long userId, boolean admin) {
        String type = action.getType();
        if (PendingAction.TYPE_CREATE_LECTURE.equals(type)) {
            validateDraft(draft, userId);
            lectureService.publish(draft);
            return draft.getId();
        }
        if (PendingAction.TYPE_UPDATE_LECTURE.equals(type)) {
            lectureService.updateByOwner(action.getTargetId(), draft, userId, admin);
            return action.getTargetId();
        }
        if (PendingAction.TYPE_CANCEL_LECTURE.equals(type)) {
            lectureService.cancelByOwner(action.getTargetId(), cancelReason(action), userId, admin);
            return action.getTargetId();
        }
        if (PendingAction.TYPE_PUBLISH_LECTURE.equals(type)) {
            lectureService.updatePublishStatusByOwner(action.getTargetId(), draft.getPublishStatus(), userId, admin);
            return action.getTargetId();
        }
        throw new ApiException("不支持的动作类型：" + type);
    }

    private PendingActionResponse saveAction(Long userId, String type, Lecture draft, Long targetId) {
        return saveAction(userId, type, draft, targetId, null);
    }

    private PendingActionResponse saveAction(Long userId, String type, Lecture draft, Long targetId, String reason) {
        PendingAction action = new PendingAction();
        action.setUserId(userId);
        action.setType(type);
        action.setTargetId(targetId);
        action.setPayload(writePayload(draft, reason));
        action.setStatus(PendingAction.STATUS_PENDING);
        save(action);
        PendingActionResponse response = response(action, draft);
        response.setReason(reason);
        return response;
    }

    private String writePayload(Lecture draft, String reason) {
        try {
            // 取消原因与草稿一起持久化，确认时不需要前端再次提交
            com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.valueToTree(draft);
            if (reason != null) {
                node.put(REASON_FIELD, reason);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new ApiException("无法保存讲座草稿", e);
        }
    }

    /**
     * 反序列化草稿：先剔除附加的取消原因字段再转 Lecture，
     * 避免依赖具体 ObjectMapper 的忽略未知字段配置。
     */
    private Lecture payload(PendingAction action) {
        try {
            JsonNode node = objectMapper.readTree(action.getPayload());
            if (node.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove(REASON_FIELD);
            }
            return objectMapper.treeToValue(node, Lecture.class);
        } catch (Exception e) {
            throw new ApiException("讲座草稿数据无效", e);
        }
    }

    private String cancelReason(PendingAction action) {
        try {
            String reason = objectMapper.readTree(action.getPayload()).path(REASON_FIELD).asText(null);
            return StringUtils.hasText(reason) ? reason : "用户取消";
        } catch (Exception e) {
            return "用户取消";
        }
    }

    private boolean hasUpdatableField(Lecture patch) {
        return patch.getTitle() != null || patch.getSummary() != null || patch.getContent() != null
                || patch.getSpeaker() != null || patch.getCategoryId() != null || patch.getLocationId() != null
                || patch.getLectureTime() != null || patch.getDurationMinutes() != null || patch.getCapacity() != null;
    }

    private void requireCreator(Long userId) {
        if (userId == null || !(roleHelper.isAdmin(userId) || roleHelper.isTeacher(userId)))
            throw new ApiException("只有教师或管理员可以创建讲座");
    }

    /** 目标讲座必须存在且属于当前用户（管理员可跨归属） */
    private Lecture requireOwnedLecture(Long lectureId, Long userId) {
        if (lectureId == null) throw new ApiException("讲座ID不能为空");
        Lecture existing = lectureService.getById(lectureId);
        if (existing == null) throw new ApiException("讲座不存在（ID=" + lectureId + "）");
        if (!roleHelper.isAdmin(userId) && !userId.equals(existing.getOrganizerId())) {
            throw new ApiException("只能操作自己的讲座");
        }
        return existing;
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
        if (d.getDurationMinutes() != null && d.getDurationMinutes() <= 0) throw new ApiException("讲座时长必须为正数");
        if (categoryService.getById(d.getCategoryId()) == null) throw new ApiException("讲座类别不存在");
        if (d.getLectureTime().isBefore(LocalDateTime.now())) throw new ApiException("讲座时间必须晚于当前时间");
        if (d.getLocationId() != null) {
            var location = locationService.getById(d.getLocationId());
            if (location == null) throw new ApiException("讲座地点不存在");
            if (location.getCapacity() != null && location.getCapacity() > 0
                    && d.getCapacity() > location.getCapacity()) throw new ApiException("讲座容量不能超过地点容量");
        }
        if (d.getOrganizerId() != null && !userId.equals(d.getOrganizerId())) throw new ApiException("不能代替其他用户创建讲座");
        d.setOrganizerId(userId);
    }

    private PendingActionResponse response(PendingAction a, Lecture d) {
        // 取消类动作的响应带上取消原因（摘要与确认卡都需要）
        return PendingActionResponse.of(a.getId(), a.getType(), a.getStatus(), d, a.getResultId(),
                PendingAction.TYPE_CANCEL_LECTURE.equals(a.getType()) ? cancelReason(a) : null);
    }
}
