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
        CapacityEstimationService.Detail d = r.detail();
        StringBuilder sb = new StringBuilder();
        sb.append("建议容量：").append(r.capacity()).append(" 人\n");
        sb.append("建议区间：").append(r.confidenceLow()).append(" ~ ").append(r.confidenceHigh()).append(" 人");
        sb.append(d.confidenceAvailable() ? "（多个统计口径的上下界）\n" : "（有效口径不足两个，为单点估计）\n");
        sb.append("统计基线：").append(r.baseCapacity()).append(" 人\n");
        sb.append("历史样本：已结束讲座 ").append(d.historyCount()).append(" 场，有效报名 ")
                .append(d.totalRegistrations()).append(" 人次（已排除取消报名）\n");
        sb.append(sampleLine("讲师口径", d.lecturerSample(), "讲师=" + r.lecturerFactor()));
        sb.append(sampleLine("分类口径", d.categorySample(), "分类=" + r.categoryFactor()));
        sb.append(sampleLine("标题关键词口径", d.keywordSample(), "关键词=" + r.keywordFactor()));
        sb.append("教室容量范围：");
        if (d.roomMin() > 0 || d.roomMax() > 0) {
            sb.append(d.roomMin()).append(" ~ ").append(d.roomMax()).append(" 人\n");
        } else {
            sb.append("未维护（未参与边界裁剪）\n");
        }
        sb.append("数据来源：学校画像").append(d.profileUsed() ? "已使用" : "缺失，校本契合度按中性处理")
                .append("；讲师资料").append(d.speakerProfileUsed() ? "已使用" : "缺失，讲师声望按中性处理").append("\n");
        sb.append("语义判断：内容热度=").append(r.contentHeat()).append("（").append(format(r.contentHeatFactor())).append("），讲师声望=")
                .append(r.speakerReputation()).append("（").append(format(r.speakerReputationFactor())).append("），校本契合度=")
                .append(r.schoolFit()).append("（").append(format(r.schoolFitFactor())).append("）\n");
        if (r.llmFallback()) {
            sb.append("不确定性：LLM 定性判断不可用，结果为纯统计数据\n");
        }
        sb.append("说明：").append(r.reason());
        return sb.toString();
    }

    /** 单个统计口径一行：样本数、历史平均有效报名、是否采用或未采用原因 */
    private String sampleLine(String label, CapacityEstimationService.SampleStat stat, String detail) {
        StringBuilder sb = new StringBuilder().append(label).append("：");
        if (stat.count() == 0) {
            return sb.append("无匹配样本").append("（").append(detail).append("）\n").toString();
        }
        sb.append("样本 ").append(stat.count()).append(" 场，平均有效报名 ")
                .append(format(stat.average())).append(" 人（").append(detail).append("）");
        if (stat.used()) {
            sb.append(" → 已采用\n");
        } else {
            sb.append(" → 未采用：").append(stat.rejectReason()).append("\n");
        }
        return sb.toString();
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
