package com.smartfitagent.agent;

import com.smartfitagent.StudyService;
import com.smartfitagent.ai.AiClient;
import com.smartfitagent.model.UserProfile;
import com.smartfitagent.model.QuizAttempt;

import java.util.List;

/**
 * 进度跟踪专项 Agent（模板方法模式）
 *
 * 专注于：
 *  - 分析用户的历史训练数据，识别进步趋势
 *  - 体能评估结果解读（历次测试对比）
 *  - 训练量统计（总时间、频率、一致性）
 *  - 饮食合规率分析
 *  - 预测达到目标体重/BMI 的时间线
 *  - 识别停滞期和改进建议
 *
 * 与 MindsetAgent 的区别：
 *  - ProgressAgent 以数据分析为核心，提供客观评估
 *  - MindsetAgent 以情感支持为核心，提供心理鼓励
 */
public class ProgressAgent extends AbstractAgent {

    private final StudyService service;

    public ProgressAgent(AiClient ai, StudyService service) {
        super(ai);
        this.service = service;
    }

    @Override
    protected String type() { return "progress"; }

    @Override
    protected String systemPrompt(UserProfile user) {
        String context = service.promptContext();
        List<QuizAttempt> attempts = service.attempts();

        // 构建评估历史摘要
        StringBuilder assessmentHistory = new StringBuilder();
        for (QuizAttempt a : attempts) {
            double percentage = a.total() > 0 ? (double) a.score() / a.total() * 100 : 0;
            assessmentHistory.append("- ").append(a.subject()).append(": ")
                .append(a.score()).append("/").append(a.total())
                .append(String.format(" (%.0f%%)", percentage)).append("\n");
        }

        int planCount = service.plans().size();
        int noteCount = service.notes().size();

        return new PromptBuilder()
            .identity("数据驱动的健身进度分析师，擅长从训练和饮食数据中提炼洞察")
            .user(user)
            .context("完整健身数据", context)
            .rule("必须基于用户的真实记录数据进行分析，不能凭空估计进度")
            .rule("用数字和百分比量化进步，避免模糊表述")
            .rule("识别训练一致性（记录了几周？最近一周有记录吗？）")
            .rule("如果记录太少（<3条），说明数据不足，引导用户开始记录")
            .rule("给出 3-6 个月的进度预测，注明预测基于当前数据，可能随实际执行调整")
            .context("进度数据摘要",
                "训练计划记录：" + planCount + " 个\n"
                + "饮食摄入记录：" + noteCount + " 条\n"
                + "体能评估历史：\n" + (assessmentHistory.length() > 0
                    ? assessmentHistory.toString() : "  暂无评估记录\n")
                + "数据完整性评分：" + calcDataScore(planCount, noteCount, attempts.size()) + "/10"
            )
            .build();
    }

    @Override
    protected String nextActions() {
        return "完成本周体能评估；更新本月体重记录；生成进度报告";
    }

    @Override
    protected String usedContext() {
        return "训练计划记录、饮食记录、体能评估历史、BMI 变化趋势";
    }

    private int calcDataScore(int plans, int notes, int assessments) {
        int score = 0;
        score += Math.min(plans, 4);       // 最多 4 分
        score += Math.min(notes, 3);       // 最多 3 分
        score += Math.min(assessments, 3); // 最多 3 分
        return score;
    }
}
