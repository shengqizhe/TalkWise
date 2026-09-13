package com.example.lecture.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Location;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.LocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 教室推荐：在给定时间区间内筛出可用教室并按容量贴合度排序。
 *
 * <p>纯规则、无 LLM：硬过滤（学校可选）→ 时间冲突排除 → 容量贴合排序。
 * 规则主体抽成静态方法 {@link #recommendRule}，便于无数据库单测。</p>
 */
@Service
@RequiredArgsConstructor
public class RoomRecommendationService {

    /** 默认返回条数（Top-N） */
    public static final int DEFAULT_TOP_N = 5;

    /** 容量不足标注文案 */
    public static final String INSUFFICIENT_CAPACITY_NOTE = "容量不足";

    private final LocationMapper locationMapper;
    private final LectureMapper lectureMapper;

    /** 单条推荐（reason 为面向用户的推荐理由） */
    public record Recommendation(String roomName, Long locationId, int roomCapacity, int requestedCapacity,
                                 int surplus, boolean capacitySufficient, String reason) {
        /** 容量不足时的缺口（正数）；容量充足时为 0 */
        public int shortage() {
            return surplus >= 0 ? 0 : -surplus;
        }
    }

    /** 推荐结果：推荐列表 + 被排除的时间冲突教室数 */
    public record RecommendResult(List<Recommendation> recommendations, int conflictExcludedCount) { }

    /**
     * 推荐教室。
     *
     * @param capacity        建议/预计容量（调用方需保证为正数）
     * @param startTime       开始时间
     * @param durationMinutes 讲座时长（分钟），非正数时回退 {@link Lecture#DEFAULT_DURATION_MINUTES}
     * @param schoolName      学校名称（可选，精确匹配忽略大小写）
     * @param topN            返回条数，非正数时使用 {@link #DEFAULT_TOP_N}
     */
    public RecommendResult recommend(int capacity, LocalDateTime startTime, int durationMinutes,
                                     String schoolName, int topN) {
        int effectiveDuration = durationMinutes > 0 ? durationMinutes : Lecture.DEFAULT_DURATION_MINUTES;
        int limit = topN > 0 ? topN : DEFAULT_TOP_N;
        LocalDateTime newEnd = startTime.plusMinutes(effectiveDuration);

        // 硬过滤：学校名称可选（trim 后忽略大小写精确匹配）
        LambdaQueryWrapper<Location> query = new LambdaQueryWrapper<>();
        String school = schoolName == null ? null : schoolName.trim();
        if (school != null && !school.isEmpty()) {
            query.apply("LOWER(school_name) = {0}", school.toLowerCase(Locale.ROOT));
        }
        List<Location> candidates = locationMapper.selectList(query);

        Set<Long> conflictedIds = new LinkedHashSet<>();
        if (candidates != null) {
            for (Location location : candidates) {
                if (location == null || location.getId() == null) {
                    continue;
                }
                List<Lecture> conflicts = lectureMapper.selectConflictingLectures(
                        location.getId(), startTime, newEnd, null);
                if (conflicts != null && !conflicts.isEmpty()) {
                    conflictedIds.add(location.getId());
                }
            }
        }
        return recommendRule(candidates, conflictedIds, capacity, limit);
    }

    /**
     * 纯规则推荐（无数据库依赖）。
     *
     * @param candidates    候选教室
     * @param conflictedIds 被时间冲突排除的教室 ID
     * @param capacity      建议容量
     * @param topN          返回条数，非正数表示不截断
     */
    public static RecommendResult recommendRule(List<Location> candidates, Set<Long> conflictedIds,
                                                int capacity, int topN) {
        List<Location> all = candidates == null ? List.of()
                : candidates.stream().filter(Objects::nonNull).toList();
        Set<Long> conflicts = conflictedIds == null ? Set.of() : conflictedIds;

        List<Location> usable = new ArrayList<>();
        int conflictExcluded = 0;
        for (Location location : all) {
            if (location.getId() != null && conflicts.contains(location.getId())) {
                conflictExcluded++;
                continue;
            }
            // 容量缺失或非正数的教室无法计算贴合度，跳过
            if (location.getCapacity() == null || location.getCapacity() <= 0) {
                continue;
            }
            usable.add(location);
        }

        // 容量充足：浪费率越小越靠前（同率时容量小者优先，更贴合）
        Comparator<Location> sufficientOrder = Comparator
                .comparingDouble((Location l) -> wasteRate(l.getCapacity(), capacity))
                .thenComparingInt(Location::getCapacity)
                .thenComparing(l -> l.getName() == null ? "" : l.getName());
        // 容量不足：缺口越小越靠前（同缺口时名称稳定排序）
        Comparator<Location> insufficientOrder = Comparator
                .comparingInt((Location l) -> capacity - l.getCapacity())
                .thenComparing(l -> l.getName() == null ? "" : l.getName());

        List<Recommendation> result = new ArrayList<>();
        usable.stream().filter(l -> l.getCapacity() >= capacity).sorted(sufficientOrder)
                .forEach(l -> result.add(build(l, capacity, true)));
        usable.stream().filter(l -> l.getCapacity() < capacity).sorted(insufficientOrder)
                .forEach(l -> result.add(build(l, capacity, false)));

        int limit = topN > 0 ? Math.min(topN, result.size()) : result.size();
        return new RecommendResult(List.copyOf(result.subList(0, limit)), conflictExcluded);
    }

    /** 浪费率：(教室容量 - 建议容量) / 教室容量；容量不足时为负值 */
    private static double wasteRate(int roomCapacity, int capacity) {
        return (roomCapacity - capacity) / (double) roomCapacity;
    }

    private static Recommendation build(Location location, int capacity, boolean sufficient) {
        int roomCapacity = location.getCapacity();
        int surplus = roomCapacity - capacity;
        StringBuilder reason = new StringBuilder();
        reason.append("建议容量 ").append(capacity).append(" 人，教室容量 ").append(roomCapacity).append(" 人，余量 ");
        if (surplus >= 0) {
            reason.append(surplus).append(" 人");
        } else {
            reason.append(-surplus).append(" 人（缺口）");
        }
        reason.append("；时间冲突：无");
        if (!sufficient) {
            reason.append("；").append(INSUFFICIENT_CAPACITY_NOTE);
        }
        return new Recommendation(location.getName(), location.getId(), roomCapacity, capacity,
                surplus, sufficient, reason.toString());
    }
}
