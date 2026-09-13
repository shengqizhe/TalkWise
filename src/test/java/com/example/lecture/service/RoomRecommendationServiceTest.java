package com.example.lecture.service;

import com.example.lecture.agent.service.RoomRecommendationService;
import com.example.lecture.entity.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** RoomRecommendationService 纯规则单测：不依赖 Spring 容器与数据库。 */
class RoomRecommendationServiceTest {

    @Test
    void ordersSufficientRoomsByWasteRateAscending() {
        var result = RoomRecommendationService.recommendRule(
                List.of(room(1L, "大礼堂", 500), room(2L, "中教室", 110), room(3L, "小教室", 200)),
                Set.of(), 100, 5);

        assertEquals(3, result.recommendations().size());
        // 110 → 浪费率 0.091 最贴合；200 → 0.5；500 → 0.8
        assertEquals("中教室", result.recommendations().get(0).roomName());
        assertEquals("小教室", result.recommendations().get(1).roomName());
        assertEquals("大礼堂", result.recommendations().get(2).roomName());
        assertTrue(result.recommendations().stream().allMatch(RoomRecommendationService.Recommendation::capacitySufficient));
    }

    @Test
    void excludesConflictingRoomsAndCountsThem() {
        var result = RoomRecommendationService.recommendRule(
                List.of(room(1L, "A101", 100), room(2L, "A102", 120), room(3L, "A103", 150)),
                Set.of(2L), 100, 5);

        assertEquals(2, result.recommendations().size());
        assertEquals(1, result.conflictExcludedCount());
        assertTrue(result.recommendations().stream().noneMatch(r -> r.locationId().equals(2L)));
    }

    @Test
    void keepsInsufficientRoomsAtTailAndMarksThem() {
        var result = RoomRecommendationService.recommendRule(
                List.of(room(1L, "小教室", 30), room(2L, "中教室", 50), room(3L, "大教室", 200)),
                Set.of(), 100, 5);

        assertEquals(3, result.recommendations().size());
        var first = result.recommendations().get(0);
        assertEquals("大教室", first.roomName());
        assertTrue(first.capacitySufficient());
        assertFalse(first.reason().contains(RoomRecommendationService.INSUFFICIENT_CAPACITY_NOTE));

        var tail = result.recommendations().subList(1, 3);
        assertTrue(tail.stream().noneMatch(RoomRecommendationService.Recommendation::capacitySufficient));
        assertTrue(tail.stream().allMatch(r ->
                r.reason().contains(RoomRecommendationService.INSUFFICIENT_CAPACITY_NOTE)));
        // 缺口小者（50）排在缺口大者（30）之前
        assertEquals("中教室", tail.get(0).roomName());
        assertEquals("小教室", tail.get(1).roomName());
        assertEquals(-50, tail.get(0).surplus());
        assertEquals(50, tail.get(0).shortage());
    }

    @Test
    void skipsRoomsWithMissingOrNonPositiveCapacity() {
        var result = RoomRecommendationService.recommendRule(
                List.of(room(1L, "无容量", null), room(2L, "零容量", 0), room(3L, "正常", 120)),
                Set.of(), 100, 5);

        assertEquals(1, result.recommendations().size());
        assertEquals("正常", result.recommendations().get(0).roomName());
        assertEquals(0, result.conflictExcludedCount());
    }

    @Test
    void truncatesToTopN() {
        var result = RoomRecommendationService.recommendRule(
                List.of(room(1L, "R1", 110), room(2L, "R2", 120), room(3L, "R3", 130), room(4L, "R4", 140)),
                Set.of(), 100, 2);

        assertEquals(2, result.recommendations().size());
        assertEquals("R1", result.recommendations().get(0).roomName());
        assertEquals("R2", result.recommendations().get(1).roomName());
    }

    @Test
    void includesReasonFieldsForEveryRecommendation() {
        var result = RoomRecommendationService.recommendRule(
                List.of(room(1L, "C301", 150), room(2L, "C302", 80)), Set.of(), 100, 5);

        for (RoomRecommendationService.Recommendation r : result.recommendations()) {
            assertTrue(r.reason().contains("建议容量 100 人"), r.reason());
            assertTrue(r.reason().contains("教室容量 " + r.roomCapacity() + " 人"), r.reason());
            assertTrue(r.reason().contains("余量"), r.reason());
            assertTrue(r.reason().contains("时间冲突：无"), r.reason());
        }
    }

    @Test
    void handlesEmptyCandidatesAndNullInputs() {
        var empty = RoomRecommendationService.recommendRule(List.of(), Set.of(), 100, 5);
        assertTrue(empty.recommendations().isEmpty());
        assertEquals(0, empty.conflictExcludedCount());

        var nulls = RoomRecommendationService.recommendRule(null, null, 100, 5);
        assertTrue(nulls.recommendations().isEmpty());
    }

    private Location room(long id, String name, Integer capacity) {
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        location.setCapacity(capacity);
        return location;
    }
}
