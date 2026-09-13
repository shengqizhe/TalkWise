package com.example.lecture.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.AgentLlmClient;
import com.example.lecture.agent.AgentProperties;
import com.example.lecture.agent.LlmUnavailableException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 讲座容量估算：有效历史报名形成统计基准，LLM 只输出三维定性判断。 */
@Slf4j
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
        // 重名教师不能直接 selectOne（会抛 TooManyResultsException 并被吞成"LLM 不可用"），按列表取首条
        User speakerProfile = null;
        if (lecturer != null && !lecturer.isBlank()) {
            List<User> matched = userMapper.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getRealName, lecturer.trim()));
            if (!matched.isEmpty()) {
                speakerProfile = matched.get(0);
            }
        }
        boolean profileUsed = profile != null && !isBlank(profile.getProfileContent());
        boolean speakerProfileUsed = speakerProfile != null;

        EstimateResult rule = estimateRule(lectures, registrations, lecturer, category, title,
                profile, properties.getCapacity(), categoryNames)
                .withContext(profileUsed, speakerProfileUsed);
        if (!properties.getCapacity().isLlmEnabled()) return rule;

        String speakerContext = speakerProfile == null
                ? "（未找到教师资料，讲师声望按中性处理）"
                : "职称/头衔：" + safe(speakerProfile.getTitle()) + "；简介：" + safe(speakerProfile.getBio());
        String prompt = "请仅输出一个 JSON 对象，不要 Markdown：{\"contentHeat\":\"HIGH|MEDIUM|LOW\","
                + "\"speakerReputation\":\"HIGH|MEDIUM|LOW\",\"schoolFit\":\"HIGH|MEDIUM|LOW\","
                + "\"reason\":\"不超过100字的理由\"}。不要输出容量数字。"
                + "标题：" + safe(title) + "；简介：" + safe(summary) + "；讲师：" + safe(lecturer)
                + "；讲师资料：" + speakerContext + "；学校画像："
                + (profileUsed ? safe(profile.getProfileContent()) : "（无，schoolFit 必须为 MEDIUM）");
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode node = parseJson(llmClient.generateText(prompt));
                if (!valid(node)) {
                    continue;
                }
                String heat = level(node, "contentHeat");
                String reputation = level(node, "speakerReputation");
                String fit = level(node, "schoolFit");
                if (!profileUsed) fit = "MEDIUM";
                double hf = properties.getCapacity().getContentHeat().getOrDefault(heat, 1.0);
                double rf = properties.getCapacity().getSpeakerReputation().getOrDefault(reputation, 1.0);
                double sf = profileUsed ? properties.getCapacity().getSchoolFit().getOrDefault(fit, 1.0) : 1.0;
                return rule.withSemantic(heat, reputation, fit, node.path("reason").asText(),
                        hf, rf, sf, properties.getCapacity().getRoundTo());
            } catch (LlmUnavailableException e) {
                // 模型故障不静默降级：上抛，由工具层统一提示管理员修复
                throw e;
            } catch (Exception e) {
                log.warn("容量语义评估第 {} 次解析失败：{}", attempt + 1, e.getMessage());
            }
        }
        // 两次都拿不到合法定性结果，视为模型功能失效，不给出可能误导的统计基线
        throw new LlmUnavailableException("容量语义评估连续两次未返回合法结果");
    }

    public static EstimateResult estimateRule(List<Lecture> lectures, List<Registration> registrations,
                                               String lecturer, String category, String keyword,
                                               int maxRoomCapacity, AgentProperties.Capacity cfg) {
        return estimateRule(lectures, registrations, lecturer, category, keyword,
                profileOf(maxRoomCapacity), cfg, Map.of());
    }

    public static EstimateResult estimateRule(List<Lecture> lectures, List<Registration> registrations,
                                               String lecturer, String category, String keyword,
                                               int maxRoomCapacity, AgentProperties.Capacity cfg,
                                               Map<Long, String> categoryNames) {
        return estimateRule(lectures, registrations, lecturer, category, keyword,
                profileOf(maxRoomCapacity), cfg, categoryNames);
    }

    private static SchoolProfile profileOf(int maxRoomCapacity) {
        SchoolProfile profile = new SchoolProfile();
        profile.setMinRoomCapacity(0);
        profile.setMaxRoomCapacity(maxRoomCapacity);
        return profile;
    }

    public static EstimateResult estimateRule(List<Lecture> lectures, List<Registration> registrations,
                                               String lecturer, String category, String keyword,
                                               SchoolProfile profile, AgentProperties.Capacity cfg,
                                               Map<Long, String> categoryNames) {
        Map<Long, Long> counts = registrations == null ? Map.of() : registrations.stream().filter(Objects::nonNull)
                .filter(r -> r.getLectureId() != null && Objects.equals(r.getStatus(), 1)
                        && (r.getDeleted() == null || Objects.equals(r.getDeleted(), 0)))
                .collect(Collectors.groupingBy(Registration::getLectureId, Collectors.counting()));
        List<Lecture> history = lectures == null ? List.of() : lectures.stream().filter(Objects::nonNull)
                .filter(l -> l.getId() != null && counts.containsKey(l.getId())).toList();
        int max = profile == null || profile.getMaxRoomCapacity() == null ? 0 : profile.getMaxRoomCapacity();
        int min = profile == null || profile.getMinRoomCapacity() == null ? 0 : profile.getMinRoomCapacity();
        double overall = average(history, counts);

        Map<Long, String> safeCategoryNames = categoryNames == null ? Map.of() : categoryNames;
        RawSample rawLecturer = rawSample(history, counts, l -> contains(l.getSpeaker(), lecturer));
        RawSample rawCategory = rawSample(history, counts, l -> {
            String name = l.getCategoryId() == null ? null : safeCategoryNames.get(l.getCategoryId());
            return contains(name == null ? null : name, category);
        });
        RawSample rawKeyword = rawSample(history, counts, l -> contains(l.getTitle(), keyword));

        SampleStat s1 = judge(rawLecturer, max, cfg);
        SampleStat s2 = judge(rawCategory, max, cfg);
        SampleStat s3 = judge(rawKeyword, max, cfg);

        List<Double> usable = new ArrayList<>();
        if (s1.used()) usable.add(s1.average());
        if (s2.used()) usable.add(s2.average());
        if (s3.used()) usable.add(s3.average());
        boolean fallback = usable.isEmpty();
        double base = fallback
                ? (overall > 0 ? overall : cfg.getDefaultCapacity())
                : usable.stream().mapToDouble(Double::doubleValue).average().orElse(cfg.getDefaultCapacity());

        int baseLow = fallback || usable.size() < 2 ? (int) Math.ceil(base)
                : (int) Math.ceil(usable.stream().mapToDouble(Double::doubleValue).min().orElse(base));
        int baseHigh = fallback || usable.size() < 2 ? (int) Math.ceil(base)
                : (int) Math.ceil(usable.stream().mapToDouble(Double::doubleValue).max().orElse(base));
        Detail detail = new Detail(history.size(), (int) counts.values().stream().mapToLong(Long::longValue).sum(),
                s1, s2, s3, min, max, false, false, usable.size() >= 2, baseLow, baseHigh);

        int value = clampRound(base, profile, cfg.getRoundTo());
        String reason = fallback
                ? "没有满足至少 " + cfg.getMinSampleLectures() + " 场且达到教室容量三分之一的统计口径，使用默认/总体基线"
                : "使用有效历史报名统计（排除已取消报名）";
        return new EstimateResult(value, (int) Math.ceil(base), 1.0,
                s1.average(), s2.average(), s3.average(),
                fallback, reason, "MEDIUM", "MEDIUM", "MEDIUM", 1.0, 1.0, 1.0,
                value, value, detail);
    }

    /** 判定单个统计口径是否可用，并给出未采用原因 */
    private static SampleStat judge(RawSample sample, int max, AgentProperties.Capacity cfg) {
        if (sample.count() < cfg.getMinSampleLectures()) {
            return new SampleStat(sample.count(), sample.average(), false,
                    "样本不足（" + sample.count() + " 场 < " + cfg.getMinSampleLectures() + " 场）");
        }
        if (!(sample.average() > 0)) {
            return new SampleStat(sample.count(), sample.average(), false, "历史平均有效报名为 0");
        }
        if (max > 0 && sample.average() < max / cfg.getMinCapacityRatio()) {
            return new SampleStat(sample.count(), sample.average(), false,
                    "低于最大教室容量的三分之一（" + sample.average() + " < " + (max / cfg.getMinCapacityRatio()) + "）");
        }
        return new SampleStat(sample.count(), sample.average(), true, null);
    }

    /**
     * 边界裁剪 + 按 roundTo 向上取整 + 取整后再次裁剪。
     * 注意：统计口径的"低于最大教室三分之一则舍弃"规则已在 judge() 中处理，
     * 这里只负责结果落在 [min, max] 区间，不再把结果强制抬高到 max/3。
     */
    private static int clampRound(double value, SchoolProfile p, int roundTo) {
        double v = value;
        int min = p == null || p.getMinRoomCapacity() == null ? 0 : p.getMinRoomCapacity();
        int max = p == null || p.getMaxRoomCapacity() == null ? 0 : p.getMaxRoomCapacity();
        if (min > 0) v = Math.max(v, min);
        if (max > 0) v = Math.min(v, max);
        int step = Math.max(1, roundTo);
        int result = (int) Math.ceil(v / step) * step;
        if (max > 0) result = Math.min(result, max);
        if (min > 0) result = Math.max(result, min);
        return Math.max(1, result);
    }

    private static RawSample rawSample(List<Lecture> all, Map<Long, Long> counts, Predicate<Lecture> predicate) {
        List<Lecture> hit = all.stream().filter(predicate).toList();
        return new RawSample(hit.size(), average(hit, counts));
    }

    private static double average(List<Lecture> ls, Map<Long, Long> c) {
        return ls.isEmpty() ? 0 : ls.stream().mapToLong(l -> c.getOrDefault(l.getId(), 0L)).average().orElse(0);
    }

    private static boolean contains(String value, String wanted) {
        return wanted != null && !wanted.isBlank() && value != null
                && value.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT));
    }

    private static JsonNode parseJson(String raw) throws Exception {
        if (raw == null) return null;
        int s = raw.indexOf('{'), e = raw.lastIndexOf('}');
        return s >= 0 && e > s ? new ObjectMapper().readTree(raw.substring(s, e + 1)) : null;
    }

    private static boolean valid(JsonNode n) {
        return n != null && n.isObject() && levelOk(n, "contentHeat") && levelOk(n, "speakerReputation")
                && levelOk(n, "schoolFit") && n.path("reason").isTextual() && !n.path("reason").asText().isBlank();
    }

    private static boolean levelOk(JsonNode n, String field) {
        return n.path(field).isTextual()
                && Set.of("HIGH", "MEDIUM", "LOW").contains(n.path(field).asText().toUpperCase(Locale.ROOT));
    }

    private static String level(JsonNode n, String field) {
        return n.path(field).asText().toUpperCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "（无）" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record RawSample(int count, double average) {
    }

    /** 单个统计口径的样本情况：样本数、历史平均有效报名、是否采用、未采用原因 */
    public record SampleStat(int count, double average, boolean used, String rejectReason) {
    }

    /** 容量估算明细：统计样本、教室容量范围、数据来源与置信区间（语义调整前的统计范围） */
    public record Detail(int historyCount, int totalRegistrations,
                         SampleStat lecturerSample, SampleStat categorySample, SampleStat keywordSample,
                         int roomMin, int roomMax, boolean profileUsed, boolean speakerProfileUsed,
                         boolean confidenceAvailable, int baseLow, int baseHigh) {
        Detail withUse(boolean useProfile, boolean useSpeakerProfile) {
            return new Detail(historyCount, totalRegistrations, lecturerSample, categorySample, keywordSample,
                    roomMin, roomMax, useProfile, useSpeakerProfile, confidenceAvailable, baseLow, baseHigh);
        }
    }

    public record EstimateResult(int capacity, int baseCapacity, double factor,
                                 double lecturerFactor, double categoryFactor, double keywordFactor,
                                 boolean sampleFallback, String reason,
                                 String contentHeat, String speakerReputation, String schoolFit,
                                 double contentHeatFactor, double speakerReputationFactor, double schoolFitFactor,
                                 int confidenceLow, int confidenceHigh, Detail detail) {

        /** 标记学校画像与教师资料是否真正参与判断 */
        EstimateResult withContext(boolean profileUsed, boolean speakerProfileUsed) {
            return new EstimateResult(capacity, baseCapacity, factor, lecturerFactor, categoryFactor, keywordFactor,
                    sampleFallback, reason, contentHeat, speakerReputation, schoolFit,
                    contentHeatFactor, speakerReputationFactor, schoolFitFactor,
                    confidenceLow, confidenceHigh, detail.withUse(profileUsed, speakerProfileUsed));
        }

        /** 应用三维语义系数，并把统计置信区间同步缩放 */
        EstimateResult withSemantic(String heat, String reputation, String fit, String semanticReason,
                                    double heatFactor, double reputationFactor, double fitFactor, int roundTo) {
            double f = heatFactor * reputationFactor * fitFactor;
            int value = clampRound(baseCapacity * f, detail.roomMin(), detail.roomMax(), roundTo);
            int low = clampRound(detail.baseLow() * f, detail.roomMin(), detail.roomMax(), roundTo);
            int high = clampRound(detail.baseHigh() * f, detail.roomMin(), detail.roomMax(), roundTo);
            return new EstimateResult(value, baseCapacity, f, lecturerFactor, categoryFactor, keywordFactor,
                    sampleFallback, reason + "；语义判断：" + semanticReason, heat, reputation, fit,
                    heatFactor, reputationFactor, fitFactor,
                    Math.min(low, high), Math.max(low, high), detail);
        }

        /** 边界裁剪 + 向上取整；min/max 为 0 表示该侧无约束 */
        private static int clampRound(double value, int min, int max, int roundTo) {
            double v = value;
            if (min > 0) v = Math.max(v, min);
            if (max > 0) v = Math.min(v, max);
            int step = Math.max(1, roundTo);
            int result = (int) Math.ceil(v / step) * step;
            if (max > 0) result = Math.min(result, max);
            if (min > 0) result = Math.max(result, min);
            return Math.max(1, result);
        }
    }
}
