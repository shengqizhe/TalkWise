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
    @Test
    void usesOnlyValidRegistrationsAndEndedLecturesAndRoundsUp() {
        AgentProperties.Capacity cfg = config();
        List<Lecture> lectures = new ArrayList<>();
        List<Registration> registrations = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            Lecture lecture = lecture(i, 3, 50 + (int) i, "讲师A", "AI");
            lectures.add(lecture);
            for (int j = 0; j < i; j++) registrations.add(reg(i, j, 1));
        }
        lectures.add(lecture(99, 1, 1000, "讲师A", "AI"));
        registrations.add(reg(99, 1, 2));

        var result = CapacityEstimationService.estimateRule(lectures, registrations,
                "讲师A", null, "AI", 150, cfg, Map.of());

        assertTrue(result.sampleFallback());
        assertEquals(50, result.capacity());
        assertEquals(1.0, result.factor(), 0.001);
    }

    @Test
    void fallsBackWhenFilteredSamplesAreBelowThresholdAndClampsToRoomBounds() {
        AgentProperties.Capacity cfg = config();
        List<Lecture> lectures = List.of(
                lecture(1, 3, 20, "甲", "安全"),
                lecture(2, 3, 200, "乙", "安全"));
        List<Registration> registrations = List.of(reg(1, 1, 1), reg(2, 1, 1), reg(2, 2, 1));

        var result = CapacityEstimationService.estimateRule(lectures, registrations,
                null, null, null, 90, cfg, Map.of());

        assertTrue(result.sampleFallback());
        assertTrue(result.capacity() >= 30);
        assertTrue(result.capacity() <= 90);
        assertTrue(result.reason().contains("基线"));
    }

    private AgentProperties.Capacity config() {
        AgentProperties.Capacity c = new AgentProperties.Capacity();
        c.setDefaultCapacity(100);
        c.setMinSampleLectures(5);
        c.setMinCapacityRatio(3);
        c.setMinFactor(0.5);
        c.setMaxFactor(2);
        c.setRoundTo(10);
        return c;
    }

    private Lecture lecture(long id, int status, int capacity, String speaker, String keywords) {
        Lecture l = new Lecture();
        l.setId(id); l.setStatus(status); l.setCapacity(capacity);
        l.setSpeaker(speaker); l.setKeywords(keywords); l.setTitle(keywords + "讲座");
        return l;
    }

    private Registration reg(long lectureId, long id, int status) {
        Registration r = new Registration();
        r.setId(id); r.setLectureId(lectureId); r.setStatus(status);
        return r;
    }
}
