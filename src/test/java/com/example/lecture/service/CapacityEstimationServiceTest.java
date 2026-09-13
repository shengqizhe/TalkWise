package com.example.lecture.service;

import com.example.lecture.agent.AgentProperties;
import com.example.lecture.agent.service.CapacityEstimationService;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Registration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CapacityEstimationServiceTest {

    /** 有效报名形成统计基线；取消报名被排除；结果按 roundTo 向上取整 */
    @Test
    void usesOnlyValidRegistrationsAndRoundsUp() {
        AgentProperties.Capacity cfg = config();
        List<Lecture> lectures = new ArrayList<>();
        List<Registration> registrations = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            lectures.add(lecture(i, 3, 50 + (int) i, "讲师A", "AI"));
            for (int j = 0; j < i; j++) registrations.add(reg(i, j, 1));
        }
        // 已取消的报名（status=2）不应计入
        Registration cancelled = reg(1, 99, 2);
        registrations.add(cancelled);

        var result = CapacityEstimationService.estimateRule(lectures, registrations,
                "讲师A", null, "AI", 150, cfg, Map.of());

        // 5 场讲师样本、平均 3 人，低于最大教室容量 150 的三分之一，故该口径不被采用
        assertEquals(5, result.detail().lecturerSample().count());
        assertFalse(result.detail().lecturerSample().used());
        assertTrue(result.detail().lecturerSample().rejectReason().contains("三分之一"));
        // 口径全部被舍弃 → 回退到总体基线，取整到 10
        assertTrue(result.sampleFallback());
        assertEquals(10, result.capacity());
    }

    /** 样本充足且高于阈值时采用统计口径，并给出明细 */
    @Test
    void adoptsQualifiedSamplesAndReportsDetail() {
        AgentProperties.Capacity cfg = config();
        List<Lecture> lectures = new ArrayList<>();
        List<Registration> registrations = new ArrayList<>();
        for (long i = 1; i <= 6; i++) {
            lectures.add(lecture(i, 3, 100, "讲师A", "人工智能"));
            for (int j = 0; j < 40; j++) registrations.add(reg(i, i * 100 + j, 1));
        }

        // 最大教室容量 100，三分之一约 33.3；平均 40 人达到阈值，口径被采用
        var result = CapacityEstimationService.estimateRule(lectures, registrations,
                "讲师A", null, "人工智能", 100, cfg, Map.of());

        assertFalse(result.sampleFallback());
        assertEquals(6, result.detail().historyCount());
        assertEquals(240, result.detail().totalRegistrations());
        assertTrue(result.detail().lecturerSample().used());
        assertNull(result.detail().lecturerSample().rejectReason());
        assertEquals(40, result.detail().baseLow());
        assertEquals(40, result.detail().baseHigh());
        assertEquals(40, result.capacity());
    }

    /** 样本不足时回退，并落在教室容量边界内 */
    @Test
    void fallsBackWhenSamplesAreBelowThresholdAndClampsToRoomBounds() {
        AgentProperties.Capacity cfg = config();
        List<Lecture> lectures = List.of(
                lecture(1, 3, 20, "甲", "安全"),
                lecture(2, 3, 200, "乙", "安全"));
        List<Registration> registrations = List.of(reg(1, 1, 1), reg(2, 1, 1), reg(2, 2, 1));

        var result = CapacityEstimationService.estimateRule(lectures, registrations,
                null, null, null, 90, cfg, Map.of());

        assertTrue(result.sampleFallback());
        assertTrue(result.capacity() >= 1);
        assertTrue(result.capacity() <= 90);
        assertTrue(result.reason().contains("基线"));
        // 无匹配口径时三个样本数均为 0
        assertEquals(0, result.detail().lecturerSample().count());
        assertEquals(90, result.detail().roomMax());
    }

    /** clampRound 修正后：结果不低于 min，不超过 max，取整后仍在边界内 */
    @Test
    void clampsToRoomBoundsWithoutForcingUpToThirdOfMax() {
        AgentProperties.Capacity cfg = config();
        // 单场 10 人、容量 500 的教室：即使 max/3=167，也不应把结果强抬过去
        List<Lecture> lectures = List.of(lecture(1, 3, 500, "讲师B", "测试"));
        List<Registration> registrations = new ArrayList<>();
        for (int j = 0; j < 10; j++) registrations.add(reg(1, j, 1));
        // 样本 1 场 < 5，走回退，base 取总体平均 10
        var result = CapacityEstimationService.estimateRule(lectures, registrations,
                "讲师B", "测试", "测试", 500, cfg, Map.of());

        assertEquals(10, result.capacity());
        assertTrue(result.capacity() < 500 / 3, "结果不应被强制抬高到 max/3");
    }

    private AgentProperties.Capacity config() {
        AgentProperties.Capacity c = new AgentProperties.Capacity();
        c.setDefaultCapacity(100);
        c.setMinSampleLectures(5);
        c.setMinCapacityRatio(3);
        c.setRoundTo(10);
        return c;
    }

    private Lecture lecture(long id, int status, int capacity, String speaker, String keywords) {
        Lecture l = new Lecture();
        l.setId(id);
        l.setStatus(status);
        l.setCapacity(capacity);
        l.setSpeaker(speaker);
        l.setKeywords(keywords);
        l.setTitle(keywords + "讲座");
        return l;
    }

    private Registration reg(long lectureId, long id, int status) {
        Registration r = new Registration();
        r.setId(id);
        r.setLectureId(lectureId);
        r.setStatus(status);
        r.setDeleted(0);
        return r;
    }
}
