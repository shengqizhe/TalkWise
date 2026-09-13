package com.example.lecture.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.common.exception.ApiException;
import com.example.lecture.dto.PendingActionResponse;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.PendingAction;
import com.example.lecture.mapper.PendingActionMapper;
import com.example.lecture.service.impl.PendingActionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 待确认动作的四类分派与归属校验测试。
 * 通过注入 Mock 依赖验证"确认时调用了哪个业务方法、是否带归属参数"，不启动 Spring 容器。
 */
class PendingActionServiceTest {

    private LectureService lectureService;
    private LectureCategoryService categoryService;
    private LocationService locationService;
    private AgentRoleHelper roleHelper;
    private PendingActionMapper actionMapper;
    private PendingActionServiceImpl service;

    private final long teacherId = 7L;
    private final long lectureId = 42L;

    @BeforeEach
    void setUp() throws Exception {
        // MyBatis-Plus 的 LambdaUpdateWrapper 需要实体元信息缓存，纯单测中手动初始化
        initTableInfo(PendingAction.class);

        lectureService = mock(LectureService.class);
        categoryService = mock(LectureCategoryService.class);
        locationService = mock(LocationService.class);
        roleHelper = mock(AgentRoleHelper.class);
        actionMapper = mock(PendingActionMapper.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new PendingActionServiceImpl(lectureService, categoryService, locationService,
                roleHelper, objectMapper);
        // ServiceImpl 的 baseMapper 是父类字段，用反射注入
        Field baseMapper = service.getClass().getSuperclass().getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, actionMapper);
    }

    /** 教师确认修改：应调用带归属校验的 updateByOwner，且不带管理员权限 */
    @Test
    void confirmUpdateDispatchesToUpdateByOwner() {
        givenTeacher();
        PendingAction action = pendingAction(PendingAction.TYPE_UPDATE_LECTURE);
        Lecture patch = new Lecture();
        patch.setId(lectureId);
        patch.setTitle("新标题");
        givenAction(action, patch);
        givenClaimSucceeds();

        PendingActionResponse response = service.confirm(teacherId, 1L);

        verify(lectureService).updateByOwner(eq(lectureId), any(Lecture.class), eq(teacherId), eq(false));
        assertEquals(PendingAction.STATUS_CONFIRMED, response.getStatus());
        assertEquals(lectureId, response.getResultId());
    }

    /** 教师确认取消：应走 cancelByOwner，并把持久化的取消原因传下去 */
    @Test
    void confirmCancelDispatchesWithReason() {
        givenTeacher();
        PendingAction action = pendingAction(PendingAction.TYPE_CANCEL_LECTURE);
        Lecture draft = new Lecture();
        draft.setId(lectureId);
        draft.setTitle("待取消讲座");
        givenAction(action, draft, "场地冲突");
        givenClaimSucceeds();

        service.confirm(teacherId, 1L);

        verify(lectureService).cancelByOwner(eq(lectureId), eq("场地冲突"), eq(teacherId), eq(false));
    }

    /** 教师确认发布：应走 updatePublishStatusByOwner 并带上目标状态 */
    @Test
    void confirmPublishDispatchesWithTargetStatus() {
        givenTeacher();
        PendingAction action = pendingAction(PendingAction.TYPE_PUBLISH_LECTURE);
        Lecture draft = new Lecture();
        draft.setId(lectureId);
        draft.setPublishStatus(1);
        givenAction(action, draft);
        givenClaimSucceeds();

        service.confirm(teacherId, 1L);

        verify(lectureService).updatePublishStatusByOwner(eq(lectureId), eq(1), eq(teacherId), eq(false));
    }

    /** 非本人动作不可确认 */
    @Test
    void confirmRejectsActionOwnedByAnotherUser() {
        PendingAction action = pendingAction(PendingAction.TYPE_UPDATE_LECTURE);
        action.setUserId(999L);
        when(actionMapper.selectById(1L)).thenReturn(action);

        ApiException ex = assertThrows(ApiException.class, () -> service.confirm(teacherId, 1L));
        assertTrue(ex.getMessage().contains("确认动作不存在"));
        verify(lectureService, never()).updateByOwner(anyLong(), any(), anyLong(), anyBoolean());
    }

    /** 已处理的动作不能重复确认（并发闸门返回 0 行） */
    @Test
    void confirmRejectsWhenAlreadyProcessed() {
        givenTeacher();
        PendingAction action = pendingAction(PendingAction.TYPE_UPDATE_LECTURE);
        Lecture patch = new Lecture();
        patch.setId(lectureId);
        givenAction(action, patch);
        when(actionMapper.update(any(), any())).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.confirm(teacherId, 1L));
        assertTrue(ex.getMessage().contains("不能重复确认"));
        verify(lectureService, never()).updateByOwner(anyLong(), any(), anyLong(), anyBoolean());
    }

    /** 管理员确认他人讲座：归属参数应为 admin=true */
    @Test
    void confirmByAdminPassesAdminFlag() {
        when(roleHelper.isAdmin(teacherId)).thenReturn(true);
        when(roleHelper.isTeacher(teacherId)).thenReturn(false);
        PendingAction action = pendingAction(PendingAction.TYPE_UPDATE_LECTURE);
        Lecture patch = new Lecture();
        patch.setId(lectureId);
        givenAction(action, patch);
        givenClaimSucceeds();

        service.confirm(teacherId, 1L);

        verify(lectureService).updateByOwner(eq(lectureId), any(Lecture.class), eq(teacherId), eq(true));
    }

    /** 修改草稿只持久化白名单字段，服务端控制字段不落库 */
    @Test
    void prepareUpdateKeepsOnlyWhitelistedFields() throws Exception {
        givenTeacher();
        when(roleHelper.isAdmin(teacherId)).thenReturn(false);
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        Lecture existing = new Lecture();
        existing.setId(lectureId);
        existing.setOrganizerId(teacherId);
        existing.setStatus(1);
        when(lectureService.getById(lectureId)).thenReturn(existing);

        Lecture patch = new Lecture();
        patch.setTitle("新标题");
        patch.setCapacity(200);
        // 恶意字段：不应进入草稿
        patch.setOrganizerId(888L);
        patch.setStatus(3);
        patch.setPublishStatus(1);
        patch.setRegisteredCount(9999);
        patch.setLectureTime(future);

        PendingActionResponse response = service.prepareLectureUpdate(teacherId, lectureId, patch);

        ArgumentCaptor<PendingAction> captor = ArgumentCaptor.forClass(PendingAction.class);
        verify(actionMapper).insert(captor.capture());
        String payload = captor.getValue().getPayload();
        assertTrue(payload.contains("新标题"));
        assertTrue(payload.contains("200"));
        assertFalse(payload.contains("888"), "organizerId 不应进入草稿");
        assertFalse(payload.contains("9999"), "registeredCount 不应进入草稿");
        assertEquals(PendingAction.TYPE_UPDATE_LECTURE, response.getType());
        assertEquals(lectureId, captor.getValue().getTargetId());
    }

    /** 教师不能修改他人讲座 */
    @Test
    void prepareUpdateRejectsForeignLecture() {
        givenTeacher();
        Lecture existing = new Lecture();
        existing.setId(lectureId);
        existing.setOrganizerId(999L);
        existing.setStatus(1);
        when(lectureService.getById(lectureId)).thenReturn(existing);

        Lecture patch = new Lecture();
        patch.setTitle("改名");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.prepareLectureUpdate(teacherId, lectureId, patch));
        assertTrue(ex.getMessage().contains("只能操作自己的讲座"));
    }

    private void givenTeacher() {
        when(roleHelper.isAdmin(teacherId)).thenReturn(false);
        when(roleHelper.isTeacher(teacherId)).thenReturn(true);
    }

    private PendingAction pendingAction(String type) {
        PendingAction action = new PendingAction();
        action.setId(1L);
        action.setUserId(teacherId);
        action.setType(type);
        action.setStatus(PendingAction.STATUS_PENDING);
        action.setTargetId(lectureId);
        return action;
    }

    private void givenAction(PendingAction action, Lecture payload) {
        givenAction(action, payload, null);
    }

    private void givenAction(PendingAction action, Lecture payload, String reason) {
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            com.fasterxml.jackson.databind.node.ObjectNode node = mapper.valueToTree(payload);
            if (reason != null) node.put("_reason", reason);
            action.setPayload(mapper.writeValueAsString(node));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(actionMapper.selectById(1L)).thenReturn(action);
    }

    private void givenClaimSucceeds() {
        when(actionMapper.update(any(), any())).thenReturn(1);
        when(actionMapper.updateById(any())).thenReturn(1);
    }

    /** 为实体类初始化 MyBatis-Plus 的 TableInfo 缓存（含 lambda 解析所需信息） */
    private static <T> void initTableInfo(Class<T> entityClass) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }
}
