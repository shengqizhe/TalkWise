package com.example.lecture.agent.tool;

import com.example.lecture.agent.AgentContext;
import com.example.lecture.agent.AgentParam;
import com.example.lecture.agent.AgentRoleHelper;
import com.example.lecture.agent.AgentTool;
import com.example.lecture.agent.service.CapacityEstimationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 面向教师和管理员的讲座容量估算工具。 */
@Component
@RequiredArgsConstructor
public class CapacityTools {
    private final CapacityEstimationService service;
    private final AgentRoleHelper roleHelper;

    @AgentTool(name = "estimateCapacity", domain = "analysis", roles = {"admin", "teacher"},
            description = "估算讲座建议报名容量。使用已结束讲座的有效报名记录，并结合内容热度、讲师声望、校本契合度三个定性维度；样本不足时说明不确定性。仅返回建议，不修改讲座数据或容量。")
    public String estimateCapacity(
            @AgentParam(name = "title", description = "拟举办讲座标题") String title,
            @AgentParam(name = "summary", description = "讲座简介，可选", required = false) String summary,
            @AgentParam(name = "lecturer", description = "讲师姓名") String lecturer,
            @AgentParam(name = "category", description = "讲座分类，可选", required = false) String category,
            @AgentParam(name = "schoolName", description = "学校名称，可选", required = false) String schoolName) {
        Long userId = AgentContext.getUserId();
        if (userId == null) return "请先登录后再估算讲座容量。";
        if (!roleHelper.isAdmin(userId) && !roleHelper.isTeacher(userId)) return "容量估算功能面向教师与管理员开放。";
        CapacityEstimationService.EstimateResult r = service.estimateWithLlm(title, summary, lecturer, category, schoolName);
        return "建议容量：" + r.capacity() + " 人\n"
                + "规则基线：" + r.baseCapacity() + " 人，综合系数：" + format(r.factor()) + "\n"
                + "统计口径因子：讲师=" + format(r.lecturerFactor()) + "，分类=" + format(r.categoryFactor()) + "，标题关键词=" + format(r.keywordFactor()) + "\n"
                + "语义判断：内容热度=" + r.contentHeat() + "（" + format(r.contentHeatFactor()) + "），讲师声望=" + r.speakerReputation() + "（" + format(r.speakerReputationFactor()) + "），校本契合度=" + r.schoolFit() + "（" + format(r.schoolFitFactor()) + ")\n"
                + "说明：" + r.reason();
    }

    private String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}
