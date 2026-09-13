package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.dto.LectureCard;
import com.example.lecture.entity.Lecture;
import com.example.lecture.entity.LectureCategory;
import com.example.lecture.entity.Location;
import com.example.lecture.mapper.LectureCategoryMapper;
import com.example.lecture.mapper.LectureMapper;
import com.example.lecture.mapper.LocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 讲座查询工具（只读，直接复用现有 Mapper）。
 *
 * <p>查询结果有两份产物：
 * <ol>
 *   <li>结构化卡片放入 {@link AgentContext}，由前端直接渲染成可点击的讲座卡片；</li>
 *   <li>返回给模型的是简短文本摘要，且<b>不含讲座 ID</b>——ID 仅供前端跳转使用，
 *       不进入对话上下文，避免模型把它当作内容复述给用户。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class LectureSearchTools {

    private final LectureMapper lectureMapper;
    private final LocationMapper locationMapper;
    private final LectureCategoryMapper categoryMapper;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 单次查询最多返回的讲座条数 */
    private static final int MAX_RESULT = 10;

    @AgentTool(
            name = "searchLectures",
            domain = "lecture",
            description = "查询系统里的讲座，可按关键词或主讲人过滤。"
                    + "关键词会匹配标题、摘要、正文、关键词以及讲座分类名（如「软件工程」「人工智能」），"
                    + "所以按学科方向找讲座也用本工具。不传条件时返回全部已发布讲座。"
                    + "适用：用户想找讲座、要推荐、或后续操作需要定位某场讲座时。"
                    + "不适用：统计数据类问题（报名排行/分类热度/签到率）请用 queryStatistics；给讲座写宣传文案请用 generatePromotion。"
    )
    public String searchLectures(
            @AgentParam(name = "keyword", description = "讲座标题、主题或分类关键词，可不传", required = false) String keyword,
            @AgentParam(name = "speaker", description = "主讲人姓名，可不传", required = false) String speaker
    ) {
        boolean hasSpeaker = speaker != null && !speaker.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        List<Lecture> lectures;
        if (hasSpeaker) {
            lectures = lectureMapper.selectBySpeaker(speaker.trim());
        } else if (hasKeyword) {
            lectures = lectureMapper.selectByKeyword(keyword.trim());
        } else {
            lectures = lectureMapper.selectAllPublished();
        }

        if (lectures == null || lectures.isEmpty()) {
            String condition = hasSpeaker ? "主讲人「" + speaker.trim() + "」"
                    : hasKeyword ? "关键词「" + keyword.trim() + "」" : "";
            // 本次查询无结果：清空上一次查询遗留的卡片，避免展示与当前问题不符的讲座
            AgentContext.replaceLectureCards(List.of());
            return condition.isEmpty() ? "系统中还没有已发布的讲座。" : "没有找到与" + condition + "相关的讲座。";
        }

        StringBuilder sb = new StringBuilder();
        List<LectureCard> cards = new ArrayList<>();
        int limit = Math.min(lectures.size(), MAX_RESULT);
        for (int i = 0; i < limit; i++) {
            Lecture lec = lectures.get(i);
            String location = locationName(lec.getLocationId());
            String category = categoryName(lec.getCategoryId());
            cards.add(toCard(lec, location, category));
            // 给模型的摘要：ID 供其调用报名/改期等后续工具使用，
            // 但按要求不得出现在给用户的回答中（由引擎层清洗 + 提示词双重约束）
            sb.append(i + 1).append(". 《").append(lec.getTitle()).append("》")
                    .append(" 讲座ID：").append(lec.getId())
                    .append(" 主讲人：").append(lec.getSpeaker())
                    .append(" 时间：").append(lec.getLectureTime() == null ? "待定" : FMT.format(lec.getLectureTime()))
                    .append(" 地点：").append(location)
                    .append(" 分类：").append(category)
                    .append(" 状态：").append(statusText(lec.getStatus()))
                    .append("\n");
        }
        // 前端只展示本次查询结果（模型可能连续换关键词查询多次）
        AgentContext.replaceLectureCards(cards);
        if (lectures.size() > limit) {
            sb.append("（共 ").append(lectures.size()).append(" 场，仅列出前 ").append(limit).append(" 场）");
        }
        sb.append("\n注：讲座ID仅供你调用后续工具使用，禁止出现在给用户的回答中；列表已由界面卡片展示，无需复述。");
        return sb.toString().trim();
    }

    private LectureCard toCard(Lecture lec, String location, String category) {
        LectureCard card = new LectureCard();
        card.setId(lec.getId());
        card.setTitle(lec.getTitle());
        card.setSpeaker(lec.getSpeaker());
        card.setLectureTime(lec.getLectureTime() == null ? "待定" : FMT.format(lec.getLectureTime()));
        card.setLocationName(location);
        card.setCategoryName(category);
        card.setStatusText(statusText(lec.getStatus()));
        card.setCapacity(lec.getCapacity());
        card.setRegisteredCount(lec.getRegisteredCount());
        return card;
    }

    private String locationName(Long locationId) {
        if (locationId == null) {
            return "未设置";
        }
        Location loc = locationMapper.selectById(locationId);
        return loc == null ? "未设置" : loc.getName();
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return "未分类";
        }
        LectureCategory category = categoryMapper.selectById(categoryId);
        return category == null || category.getCategoryName() == null ? "未分类" : category.getCategoryName();
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
