package com.example.lecture.agent;

import com.example.lecture.agent.dto.LectureCard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 执行上下文：当前请求的用户身份透传给工具方法。
 * 工具不应信任模型传入的 userId，只能从这里取当前登录用户，防止越权。
 *
 * <p>同时承载本次请求产出的结构化卡片：工具查询到讲座后放入这里，
 * 由引擎随响应一次性返回前端渲染。这样不依赖模型复述列表——
 * 模型可能改写成 Markdown 表格，或把仅供内部使用的讲座 ID 暴露给用户。
 */
public final class AgentContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();
    /** 本次请求收集到的讲座卡片（按查询顺序） */
    private static final ThreadLocal<List<LectureCard>> LECTURE_CARDS =
            ThreadLocal.withInitial(ArrayList::new);
    /** 已收集的讲座ID，用于跨多次工具调用去重 */
    private static final ThreadLocal<Set<Long>> CARD_IDS =
            ThreadLocal.withInitial(LinkedHashSet::new);

    private AgentContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /** 可能为 null（未登录游客），工具需自行处理 */
    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setRoles(Set<String> roles) {
        ROLES.set(roles == null ? Set.of() : roles);
    }

    /** 当前用户角色（小写）集合；未设置时为空集 */
    public static Set<String> getRoles() {
        Set<String> roles = ROLES.get();
        return roles == null ? Set.of() : roles;
    }

    /** 收集讲座卡片；同一讲座重复出现时只保留首次（多轮追加场景，如详情工具补充） */
    public static void addLectureCard(LectureCard card) {
        if (card == null || card.getId() == null) {
            return;
        }
        if (CARD_IDS.get().add(card.getId())) {
            LECTURE_CARDS.get().add(card);
        }
    }

    /**
     * 以一次查询的结果替换当前卡片集合。
     *
     * <p>模型可能用不同关键词连续查多次（先"软件"再"软件工程"），
     * 若累积展示会把不相关的结果一并带出；最终展示的应是最后一次查询的结果。</p>
     */
    public static void replaceLectureCards(List<LectureCard> cards) {
        clearLectureCards();
        if (cards != null) {
            cards.forEach(AgentContext::addLectureCard);
        }
    }

    /** 本次请求收集到的讲座卡片（返回副本，避免调用方改动内部状态） */
    public static List<LectureCard> getLectureCards() {
        return new ArrayList<>(LECTURE_CARDS.get());
    }

    /** 清空卡片收集（每次对话开始前调用，避免同线程上一个请求的残留） */
    public static void clearLectureCards() {
        LECTURE_CARDS.get().clear();
        CARD_IDS.get().clear();
    }

    public static void clear() {
        USER_ID.remove();
        ROLES.remove();
        LECTURE_CARDS.remove();
        CARD_IDS.remove();
    }
}
