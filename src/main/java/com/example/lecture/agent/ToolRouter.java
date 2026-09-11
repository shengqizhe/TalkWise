package com.example.lecture.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具路由器：在把工具下发给模型之前做两层过滤——
 *   ① 身份过滤：按当前用户角色裁剪（学生看不到管理类工具）；
 *   ② 意图过滤：按用户消息命中"工具域"，只下发相关域的工具（+ lecture 基础域与 general 兜底）。
 *
 * 目的：上下文里候选工具更少 → 模型选择更准、token 更省。
 * 兜底策略：未命中任何域、或过滤后为空时，不裁剪（宁可多给，不可漏给）。
 *
 * 演进路径：当前为关键词路由（工具 < 15 个足够）；
 * 工具增多后可升级为 embedding 检索路由（工具描述向量化，按问题召回 Top-K）。
 */
@Slf4j
@Component
public class ToolRouter {

    /** 域 → 关键词表（命中任意关键词即认为需要该域工具） */
    private static final Map<String, List<String>> DOMAIN_KEYWORDS = Map.of(
            "statistics", List.of("统计", "多少", "几个", "数量", "排行", "排名", "分布",
                    "热度", "签到率", "报表", "趋势", "最活跃", "最多", "占比"),
            "analysis", List.of("评价", "反馈", "情感", "满意度", "分析"),
            "content", List.of("文案", "宣传", "推文", "海报", "标题", "简介", "介绍词", "怎么写"),
            "registration", List.of("报名", "预约", "取消", "我报了", "我的预约", "待参加"),
            "lecture", List.of("讲座", "安排", "活动", "推荐", "有什么", "近期", "这周", "周末", "本月")
    );

    /** 无论如何都保留的基础域（讲座查询是系统核心入口，报名/推荐都依赖它拿 ID） */
    private static final Set<String> BASE_DOMAINS = Set.of("lecture", "general");

    /**
     * @param all     注册表全量工具
     * @param message 用户消息
     * @param roles   当前用户角色（小写集合）
     * @return 本次实际下发给模型的工具子集
     */
    public List<AgentToolRegistry.ToolDefinition> route(
            List<AgentToolRegistry.ToolDefinition> all, String message, Set<String> roles) {

        // ① 身份过滤
        List<AgentToolRegistry.ToolDefinition> byRole = all.stream()
                .filter(def -> hasRole(def.roles, roles))
                .toList();

        // ② 意图过滤
        String msg = message == null ? "" : message;
        Set<String> hitDomains = DOMAIN_KEYWORDS.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(msg::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (hitDomains.isEmpty()) {
            log.info("[Agent路由] 身份过滤后 {} 个工具；意图未命中域，不做裁剪", byRole.size());
            return byRole;
        }

        List<AgentToolRegistry.ToolDefinition> routed = byRole.stream()
                .filter(def -> hitDomains.contains(def.domain) || BASE_DOMAINS.contains(def.domain))
                .toList();
        if (routed.isEmpty()) {
            return byRole;
        }
        log.info("[Agent路由] 命中域 {}，工具 {} → {} 个", hitDomains, byRole.size(), routed.size());
        return routed;
    }

    private boolean hasRole(String[] required, Set<String> roles) {
        if (required == null || required.length == 0) {
            return true; // 未限定角色 = 公开工具
        }
        for (String req : required) {
            for (String r : roles) {
                if (r.contains(req)) {
                    return true;
                }
            }
        }
        return false;
    }
}
