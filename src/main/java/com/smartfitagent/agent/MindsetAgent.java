package com.smartfitagent.agent;

import com.smartfitagent.StudyService;
import com.smartfitagent.ai.AiClient;
import com.smartfitagent.model.UserProfile;

/**
 * 心态与动力专项 Agent（模板方法模式）
 *
 * 专注于：
 *  - 维持训练动力（基于进度数据给出鼓励）
 *  - 克服训练平台期心理障碍
 *  - 建立健康的运动习惯循环
 *  - 应对健身焦虑和身材焦虑
 *  - 目标设定（SMART 原则）
 *  - 自我效能感培养
 *
 * 不同于其他专业 Agent，MindsetAgent 更注重情感支持和正向强化，
 * 用数据量化进步，将抽象的心理问题转化为可行动的步骤。
 */
public class MindsetAgent extends AbstractAgent {

    private final StudyService service;

    public MindsetAgent(AiClient ai, StudyService service) {
        super(ai);
        this.service = service;
    }

    @Override
    protected String type() { return "mindset"; }

    @Override
    protected String systemPrompt(UserProfile user) {
        String context = service.promptContext();
        int planCount = service.plans().size();
        int noteCount = service.notes().size();
        int attempts = service.attempts().size();
        int totalActivity = planCount + noteCount + attempts;

        String engagementLevel;
        String encouragement;
        if (totalActivity >= 15) {
            engagementLevel = "高度活跃";
            encouragement = "用户已经建立了非常好的健身习惯，重点在于维持和持续进步";
        } else if (totalActivity >= 5) {
            engagementLevel = "正在养成习惯";
            encouragement = "用户正在建立健身习惯，需要强化进步感和成就感";
        } else {
            engagementLevel = "刚开始起步";
            encouragement = "用户刚开始健身之旅，最重要的是降低门槛、建立自信";
        }

        return new PromptBuilder()
            .identity("心理健康教练，专注于运动心理学和行为改变，擅长帮助用户克服健身心理障碍")
            .user(user)
            .context("用户活跃度数据", context)
            .rule("基于用户实际的记录数量给出有根据的鼓励，不要空泛地说'你很棒'")
            .rule("用具体进步指标量化成就（如：你已坚持 X 天，记录了 Y 个计划）")
            .rule("给出 1-3 个具体可操作的下一步行动，不要列出 10 条建议")
            .rule("语气温暖友好，但保持专业，避免过度煽情")
            .rule("如用户表达明显的负面情绪（放弃、绝望），优先表示理解和共情")
            .context("心态背景分析",
                "用户参与度：" + engagementLevel + "（共 " + totalActivity + " 条记录）\n"
                + "教练策略：" + encouragement + "\n"
                + "已完成训练计划：" + planCount + " 个\n"
                + "饮食记录：" + noteCount + " 条\n"
                + "体能评估：" + attempts + " 次"
            )
            .build();
    }

    @Override
    protected String nextActions() {
        return "设置本周小目标；完成今日训练打卡；分享进度到挑战广场";
    }

    @Override
    protected String usedContext() {
        return "用户活跃度数据、记录数量、参与度评估";
    }
}
