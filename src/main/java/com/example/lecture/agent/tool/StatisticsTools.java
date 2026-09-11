package com.example.lecture.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.LectureCategory;
import com.example.lecture.entity.Registration;
import com.example.lecture.entity.User;
import com.example.lecture.mapper.DepartmentMapper;
import com.example.lecture.mapper.LectureCategoryMapper;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.RegistrationMapper;
import com.example.lecture.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统计问答工具（只读）：模型把自然语言问题映射到预定义指标，由代码聚合数据——不生成 SQL。
 * 数据范围：管理员=全平台；教师=自己的讲座；学生无权使用。
 */
@Component
@RequiredArgsConstructor
public class StatisticsTools {

    private final LectureMapper lectureMapper;
    private final RegistrationMapper registrationMapper;
    private final LectureCategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final AgentRoleHelper roleHelper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM月dd日");

    @AgentTool(
            name = "queryStatistics",
            domain = "statistics",
            roles = {"admin", "teacher"},
            description = "查询平台的讲座与报名统计数据，用于回答数据类问题。metric 取值：" +
                    "overview=总体概览（讲座数/报名数/本周新增报名/签到率）；" +
                    "categories=各讲座分类的热度排名（场次与报名数）；" +
                    "departments=各院系报名人数分布排名；" +
                    "top_lectures=报名人数最多的讲座排行。" +
                    "管理员统计全平台，教师仅统计自己的讲座。" +
                    "不适用：查询具体讲座的标题/时间等信息请用 searchLectures。"
    )
    public String queryStatistics(
            @AgentParam(name = "metric", description = "指标：overview / categories / departments / top_lectures") String metric
    ) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "请先登录后再查询统计数据。";
        }
        boolean admin = roleHelper.isAdmin(userId);
        boolean teacher = roleHelper.isTeacher(userId);
        if (!admin && !teacher) {
            return "统计查询功能面向教师与管理员开放。";
        }

        List<Lecture> lectures = lectureMapper.selectList(null);
        if (!admin) {
            lectures = lectures.stream()
                    .filter(l -> userId.equals(l.getOrganizerId()))
                    .toList();
        }
        if (lectures.isEmpty()) {
            return "暂无讲座数据可统计。";
        }
        List<Long> lectureIds = lectures.stream().map(Lecture::getId).toList();

        // 有效报名（status=1，已取消的不算）
        List<Registration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().in(Registration::getLectureId, lectureIds));
        registrations = registrations.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .toList();
        final List<Registration> validRegs = registrations;

        String m = metric == null ? "" : metric.trim().toLowerCase();
        return switch (m) {
            case "categories" -> categories(lectures, validRegs);
            case "departments" -> departments(validRegs);
            case "top_lectures" -> topLectures(lectures, validRegs);
            default -> overview(lectures, validRegs);
        };
    }

    /** 总体概览 */
    private String overview(List<Lecture> lectures, List<Registration> regs) {
        long published = lectures.stream().filter(l -> l.getPublishStatus() != null && l.getPublishStatus() == 1).count();
        long finished = lectures.stream().filter(l -> l.getStatus() != null && l.getStatus() == 3).count();
        long weekNew = regs.stream()
                .filter(r -> r.getRegisterTime() != null && !r.getRegisterTime().isBefore(weekStart()))
                .count();
        long checked = regs.stream().filter(r -> r.getCheckinStatus() != null && r.getCheckinStatus() == 1).count();
        String rate = regs.isEmpty() ? "—" : String.format("%.1f%%", checked * 100.0 / regs.size());
        return "统计概览：\n"
                + "- 讲座总数：" + lectures.size() + " 场（已发布 " + published + "，已结束 " + finished + "）\n"
                + "- 报名总数：" + regs.size() + " 人次\n"
                + "- 本周新增报名：" + weekNew + " 人次\n"
                + "- 整体签到率：" + rate + "（已签到 " + checked + " / 报名 " + regs.size() + "）";
    }

    /** 分类热度排名 */
    private String categories(List<Lecture> lectures, List<Registration> regs) {
        Map<Long, Long> regCountByLecture = regs.stream()
                .collect(Collectors.groupingBy(Registration::getLectureId, Collectors.counting()));
        Map<Long, String> catNames = categoryNameMap(lectures);
        Map<String, long[]> stat = new HashMap<>(); // name -> [场次, 报名数]
        for (Lecture l : lectures) {
            String name = l.getCategoryId() == null ? "未分类" : catNames.getOrDefault(l.getCategoryId(), "未分类");
            long[] s = stat.computeIfAbsent(name, k -> new long[2]);
            s[0]++;
            s[1] += regCountByLecture.getOrDefault(l.getId(), 0L);
        }
        if (stat.isEmpty()) {
            return "暂无分类统计数据。";
        }
        StringBuilder sb = new StringBuilder("各分类热度排名（按报名数）：\n");
        stat.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[1]).reversed())
                .limit(5)
                .forEach(e -> sb.append("- ").append(e.getKey())
                        .append("：").append(e.getValue()[0]).append(" 场，报名 ")
                        .append(e.getValue()[1]).append(" 人次\n"));
        return sb.toString().trim();
    }

    /** 院系报名分布 */
    private String departments(List<Registration> regs) {
        if (regs.isEmpty()) {
            return "暂无报名数据。";
        }
        List<Long> userIds = regs.stream().map(Registration::getUserId).distinct().toList();
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> deptNames = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(d -> d.getId(), d -> d.getDepartmentName()));
        Map<String, Long> countByDept = regs.stream().collect(Collectors.groupingBy(r -> {
            User u = users.get(r.getUserId());
            if (u == null || u.getDepartmentId() == null) {
                return "未知院系";
            }
            return deptNames.getOrDefault(u.getDepartmentId(), "未知院系");
        }, Collectors.counting()));
        StringBuilder sb = new StringBuilder("各院系报名人数排名：\n");
        countByDept.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append(" 人次\n"));
        return sb.toString().trim();
    }

    /** 报名最多的讲座 */
    private String topLectures(List<Lecture> lectures, List<Registration> regs) {
        Map<Long, Long> regCount = regs.stream()
                .collect(Collectors.groupingBy(Registration::getLectureId, Collectors.counting()));
        StringBuilder sb = new StringBuilder("报名人数最多的讲座：\n");
        lectures.stream()
                .sorted(Comparator.comparingLong((Lecture l) -> regCount.getOrDefault(l.getId(), 0L)).reversed())
                .limit(5)
                .forEach(l -> sb.append("- 《").append(l.getTitle()).append("》")
                        .append("：报名 ").append(regCount.getOrDefault(l.getId(), 0L)).append(" 人")
                        .append(l.getLectureTime() == null ? "" : "（" + FMT.format(l.getLectureTime()) + "）")
                        .append("\n"));
        return sb.toString().trim();
    }

    private Map<Long, String> categoryNameMap(List<Lecture> lectures) {
        List<Long> ids = lectures.stream().map(Lecture::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(LectureCategory::getId, LectureCategory::getCategoryName));
    }

    private LocalDateTime weekStart() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        return monday.atStartOfDay();
    }
}
