package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.Location;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.LocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 讲座查询工具（只读，直接复用现有 Mapper）
 */
@Component
@RequiredArgsConstructor
public class LectureSearchTools {

    private final LectureMapper lectureMapper;
    private final LocationMapper locationMapper;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    @AgentTool(
            name = "searchLectures",
            domain = "lecture",
            description = "查询系统里的讲座：可按标题/主题关键词或主讲人过滤，不传条件返回最近发布的讲座；" +
                    "返回标题、讲座ID、主讲人、时间、地点、状态。适用：用户想找讲座、要推荐、后续操作需要讲座 ID 时。"
                    + "不适用：统计数据类问题（报名排行/分类热度/签到率）请用 queryStatistics；给讲座写宣传文案请用 generatePromotion。"
    )
    public String searchLectures(
            @AgentParam(name = "keyword", description = "讲座标题或主题关键词，可不传", required = false) String keyword,
            @AgentParam(name = "speaker", description = "主讲人姓名，可不传", required = false) String speaker
    ) {
        List<Lecture> lectures;
        boolean hasSpeaker = speaker != null && !speaker.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasSpeaker) {
            lectures = lectureMapper.selectBySpeaker(speaker.trim());
        } else if (hasKeyword) {
            lectures = lectureMapper.selectByKeyword(keyword.trim());
        } else {
            lectures = lectureMapper.selectAllPublished();
        }
        if (lectures == null || lectures.isEmpty()) {
            return "没有找到符合条件的讲座。";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(lectures.size(), 10);
        for (int i = 0; i < limit; i++) {
            Lecture lec = lectures.get(i);
            sb.append(i + 1).append(". 《").append(lec.getTitle()).append("》")
                    .append("（ID：").append(lec.getId()).append("）")
                    .append(" 主讲人：").append(lec.getSpeaker())
                    .append(" 时间：").append(lec.getLectureTime() == null ? "待定" : FMT.format(lec.getLectureTime()))
                    .append(" 地点：").append(locationName(lec.getLocationId()))
                    .append(" 状态：").append(statusText(lec.getStatus()))
                    .append("\n");
        }
        if (lectures.size() > limit) {
            sb.append("（共 ").append(lectures.size()).append(" 场，仅列出前 ").append(limit).append(" 场）");
        }
        return sb.toString().trim();
    }

    private String locationName(Long locationId) {
        if (locationId == null) {
            return "未设置";
        }
        Location loc = locationMapper.selectById(locationId);
        return loc == null ? "未设置" : loc.getName();
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "即将开始";
            case 2 -> "进行中";
            case 3 -> "已结束";
            case 4 -> "已取消";
            default -> "未知";
        };
    }
}
