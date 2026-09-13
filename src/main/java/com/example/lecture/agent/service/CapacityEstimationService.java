package com.example.lecture.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.AgentLlmClient;
import com.example.lecture.agent.AgentProperties;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.LectureCategory;
import com.example.lecture.entity.Registration;
import com.example.lecture.entity.SchoolProfile;
import com.example.lecture.entity.User;
import com.example.lecture.mapper.LectureCategoryMapper;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.RegistrationMapper;
import com.example.lecture.mapper.UserMapper;
import com.example.lecture.service.SchoolProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 讲座容量估算：有效历史报名形成统计基准，LLM 只输出三维定性判断。 */
@Service
@RequiredArgsConstructor
public class CapacityEstimationService {
    private final LectureMapper lectureMapper;
    private final RegistrationMapper registrationMapper;
    private final LectureCategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final SchoolProfileService schoolProfileService;
    private final AgentLlmClient llmClient;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EstimateResult estimateWithLlm(String title, String summary, String lecturer,
                                          String category, String schoolName) {
        List<Lecture> lectures = lectureMapper.selectList(new LambdaQueryWrapper<Lecture>().eq(Lecture::getStatus, 3));
        List<Registration> registrations = registrationMapper.selectList(null).stream()
                .filter(r -> Objects.equals(r.getStatus(), 1) && Objects.equals(r.getDeleted(), 0)).toList();
        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(LectureCategory::getId, LectureCategory::getCategoryName, (a, b) -> a));
        SchoolProfile profile = schoolName == null || schoolName.isBlank() ? null
                : schoolProfileService.getBySchoolName(schoolName.trim());
        if (profile == null) {
            List<SchoolProfile> profiles = schoolProfileService.getAllProfiles();
            profile = profiles == null || profiles.isEmpty() ? null : profiles.get(0);
        }
        EstimateResult rule = estimateRule(lectures, registrations, lecturer, category, title,
                profile, properties.getCapacity(), categoryNames);
        if (!properties.getCapacity().isLlmEnabled()) return rule;
        User speakerProfile = lecturer == null || lecturer.isBlank() ? null
                : userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getRealName, lecturer.trim()));
        String speakerContext = speakerProfile == null ? "（未找到教师资料，讲师声望只能按中性处理）"
                : "职称/头衔：" + safe(speakerProfile.getTitle()) + "；简介：" + safe(speakerProfile.getBio());
        String prompt = "请仅输出一个 JSON 对象，不要 Markdown：{\"contentHeat\":\"HIGH|MEDIUM|LOW\","
                + "\"speakerReputation\":\"HIGH|MEDIUM|LOW\",\"schoolFit\":\"HIGH|MEDIUM|LOW\","
                + "\"reason\":\"不超过100字的理由\"}。不要输出容量数字。"
                + "标题：" + safe(title) + "；简介：" + safe(summary) + "；讲师：" + safe(lecturer)
                + "；讲师资料：" + speakerContext + "；学校画像：" + (profile == null ? "（无，schoolFit 必须为 MEDIUM）" : safe(profile.getProfileContent()));
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode node = parseJson(llmClient.generateText(prompt));
                if (!valid(node)) continue;
                String heat = level(node, "contentHeat");
                String reputation = level(node, "speakerReputation");
                String fit = level(node, "schoolFit");
                if (profile == null || isBlank(profile.getProfileContent())) fit = "MEDIUM";
                double hf = properties.getCapacity().getContentHeat().getOrDefault(heat, 1.0);
                double rf = properties.getCapacity().getSpeakerReputation().getOrDefault(reputation, 1.0);
                double sf = profile == null || isBlank(profile.getProfileContent()) ? 1.0
                        : properties.getCapacity().getSchoolFit().getOrDefault(fit, 1.0);
                int value = clampRound(rule.baseCapacity() * hf * rf * sf, profile, properties.getCapacity().getRoundTo());
                return rule.withSemantic(value, hf * rf * sf, heat, reputation, fit,
                        node.path("reason").asText(), hf, rf, sf);
            } catch (Exception ignored) { }
        }
        return rule.withLlmFallback("LLM 定性结果不可用，已使用统计基线");
    }

    public static EstimateResult estimateRule(List<Lecture> lectures, List<Registration> registrations,
                                               String lecturer, String category, String keyword,
                                               int maxRoomCapacity, AgentProperties.Capacity cfg) {
        SchoolProfile profile = new SchoolProfile();
        profile.setMinRoomCapacity(0);
        profile.setMaxRoomCapacity(maxRoomCapacity);
        return estimateRule(lectures, registrations, lecturer, category, keyword, profile, cfg, Map.of());
    }

    public static EstimateResult estimateRule(List<Lecture> lectures, List<Registration> registrations,
                                               String lecturer, String category, String keyword,
                                               int maxRoomCapacity, AgentProperties.Capacity cfg,
                                               Map<Long, String> categoryNames) {
        SchoolProfile profile = new SchoolProfile();
        profile.setMinRoomCapacity(0);
        profile.setMaxRoomCapacity(maxRoomCapacity);
        return estimateRule(lectures, registrations, lecturer, category, keyword, profile, cfg, categoryNames);
    }

    public static EstimateResult estimateRule(List<Lecture> lectures, List<Registration> registrations,
                                               String lecturer, String category, String keyword,
                                               SchoolProfile profile, AgentProperties.Capacity cfg,
                                               Map<Long, String> categoryNames) {
        Map<Long, Long> counts = registrations == null ? Map.of() : registrations.stream().filter(Objects::nonNull)
                .filter(r -> r.getLectureId() != null && Objects.equals(r.getStatus(), 1) && (r.getDeleted() == null || Objects.equals(r.getDeleted(), 0)))
                .collect(Collectors.groupingBy(Registration::getLectureId, Collectors.counting()));
        List<Lecture> history = lectures == null ? List.of() : lectures.stream().filter(Objects::nonNull)
                .filter(l -> l.getId() != null && counts.containsKey(l.getId())).toList();
        int max = profile == null || profile.getMaxRoomCapacity() == null ? 0 : profile.getMaxRoomCapacity();
        int min = profile == null || profile.getMinRoomCapacity() == null ? 0 : profile.getMinRoomCapacity();
        double overall = average(history, counts);
        Sample s1 = sample(history, counts, l -> contains(l.getSpeaker(), lecturer));
        Map<Long, String> safeCategoryNames = categoryNames == null ? Map.of() : categoryNames;
        Sample s2 = sample(history, counts, l -> {
            String name = l.getCategoryId() == null ? null : safeCategoryNames.get(l.getCategoryId());
            return contains(name == null ? String.valueOf(l.getCategoryId()) : name, category);
        });
        Sample s3 = sample(history, counts, l -> contains(l.getTitle(), keyword));
        List<Double> usable = new ArrayList<>();
        if (s1.count >= cfg.getMinSampleLectures() && acceptable(s1.average, max, cfg)) usable.add(s1.average);
        if (s2.count >= cfg.getMinSampleLectures() && acceptable(s2.average, max, cfg)) usable.add(s2.average);
        if (s3.count >= cfg.getMinSampleLectures() && acceptable(s3.average, max, cfg)) usable.add(s3.average);
        boolean fallback = usable.isEmpty();
        double base = fallback ? (overall > 0 ? overall : cfg.getDefaultCapacity()) : usable.stream().mapToDouble(Double::doubleValue).average().orElse(cfg.getDefaultCapacity());
        int value = clampRound(base, profile, cfg.getRoundTo());
        String reason = fallback ? "没有满足至少 " + cfg.getMinSampleLectures() + " 场且达到教室容量三分之一的统计口径，使用默认/总体基线" : "使用有效历史报名统计（排除已取消报名）";
        return new EstimateResult(value, (int) Math.ceil(base), 1.0, s1.average, s2.average, s3.average,
                fallback, reason, false, "MEDIUM", "MEDIUM", profile == null || isBlank(profile.getProfileContent()) ? "MEDIUM" : "MEDIUM", 1.0, 1.0, 1.0);
    }

    private static boolean acceptable(double value, int max, AgentProperties.Capacity cfg) {
        return value > 0 && (max <= 0 || value >= max / cfg.getMinCapacityRatio());
    }
    private static int clampRound(double value, SchoolProfile p, int roundTo) {
        double v = value;
        int min = p == null || p.getMinRoomCapacity() == null ? 0 : p.getMinRoomCapacity();
        int max = p == null || p.getMaxRoomCapacity() == null ? 0 : p.getMaxRoomCapacity();
        if (min > 0) v = Math.max(v, min);
        if (max > 0) v = Math.max(v, Math.ceil(max / 3.0));
        if (max > 0) v = Math.min(v, max);
        int step = Math.max(1, roundTo);
        int result = (int) Math.ceil(v / step) * step;
        if (max > 0) result = Math.min(result, max);
        if (min > 0) result = Math.max(result, min);
        return Math.max(1, result);
    }
    private static Sample sample(List<Lecture> all, Map<Long, Long> counts, Function<Lecture, Boolean> predicate) {
        List<Lecture> hit = all.stream().filter(l -> predicate.apply(l)).toList();
        return new Sample(hit.size(), average(hit, counts));
    }
    private static double average(List<Lecture> ls, Map<Long, Long> c) { return ls.isEmpty() ? 0 : ls.stream().mapToLong(l -> c.getOrDefault(l.getId(), 0L)).average().orElse(0); }
    private static boolean contains(String value, String wanted) { return wanted != null && !wanted.isBlank() && value != null && value.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT)); }
    private static JsonNode parseJson(String raw) throws Exception { if (raw == null) return null; int s = raw.indexOf('{'), e = raw.lastIndexOf('}'); return s >= 0 && e > s ? new ObjectMapper().readTree(raw.substring(s, e + 1)) : null; }
    private static boolean valid(JsonNode n) { return n != null && n.isObject() && levelOk(n, "contentHeat") && levelOk(n, "speakerReputation") && levelOk(n, "schoolFit") && n.path("reason").isTextual() && !n.path("reason").asText().isBlank(); }
    private static boolean levelOk(JsonNode n, String field) { return n.path(field).isTextual() && Set.of("HIGH", "MEDIUM", "LOW").contains(n.path(field).asText().toUpperCase(Locale.ROOT)); }
    private static String level(JsonNode n, String field) { return n.path(field).asText().toUpperCase(Locale.ROOT); }
    private static String safe(String s) { return s == null ? "（无）" : s; }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private record Sample(int count, double average) {}

    public record EstimateResult(int capacity, int baseCapacity, double factor, double lecturerFactor, double categoryFactor, double keywordFactor,
                                 boolean sampleFallback, String reason, boolean llmFallback, String contentHeat, String speakerReputation, String schoolFit,
                                 double contentHeatFactor, double speakerReputationFactor, double schoolFitFactor) {
        EstimateResult withSemantic(int value, double f, String heat, String reputation, String fit, String semanticReason,
                                    double heatFactor, double reputationFactor, double fitFactor) {
            return new EstimateResult(value, baseCapacity, f, lecturerFactor, categoryFactor, keywordFactor, sampleFallback,
                    reason + "；语义判断：" + semanticReason, false, heat, reputation, fit,
                    heatFactor, reputationFactor, fitFactor);
        }
        EstimateResult withLlmFallback(String r) { return new EstimateResult(capacity, baseCapacity, factor, lecturerFactor, categoryFactor, keywordFactor, sampleFallback, reason + "；" + r, true, contentHeat, speakerReputation, schoolFit, contentHeatFactor, speakerReputationFactor, schoolFitFactor); }
    }
}
