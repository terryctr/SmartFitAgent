package com.smartfitagent.agent;

import com.smartfitagent.StudyService;
import com.smartfitagent.ai.AiClient;
import com.smartfitagent.model.UserProfile;

/**
 * 挑战赛专项 Agent（模板方法模式）
 *
 * 专注于：
 *  - 推荐适合用户当前体能的挑战
 *  - 制定挑战完成策略（周计划）
 *  - 挑战进度鼓励和调整
 *  - 积分/成就系统解释
 *  - 与其他用户竞赛的心态指导
 *  - 挑战失败后的重启建议
 *
 * ChallengeAgent 特别之处：
 *  - 主动推荐 3 个匹配用户体能的挑战
 *  - 结合用户的历史记录评估可完成性
 *  - 生成个性化的挑战打卡计划
 */
public class ChallengeAgent extends AbstractAgent {

    private final StudyService service;

    public ChallengeAgent(AiClient ai, StudyService service) {
        super(ai);
        this.service = service;
    }

    @Override
    protected String type() { return "challenge"; }

    @Override
    protected String systemPrompt(UserProfile user) {
        String context = service.promptContext();
        String mode = user != null ? user.preferredStyle() : "减脂与力量并行";
        String goal = user != null ? user.goal() : "匀称体型";

        // 根据训练偏好推荐挑战类型
        String challengeRecs;
        if (mode.contains("只做力量") || mode.contains("增肌")) {
            challengeRecs = "力量挑战（30天深蹲、引体向上进阶、卧推递增）";
        } else if (mode.contains("只做减脂") || mode.contains("减脂")) {
            challengeRecs = "有氧挑战（万步走、21天慢跑、HIIT打卡）";
        } else {
            challengeRecs = "综合挑战（30天深蹲 + 万步走 + 蛋白质摄入打卡）";
        }

        return new PromptBuilder()
            .identity("健身挑战策划师，擅长设计个性化挑战方案，帮助用户通过游戏化机制维持训练动力")
            .user(user)
            .context("用户完整档案", context)
            .rule("根据用户当前体能水平推荐 2-3 个具体可参与的挑战")
            .rule("每个挑战必须包含：名称、目标、时长、每日要求、完成奖励积分")
            .rule("挑战难度必须与用户 BMI 和训练历史相匹配")
            .rule("给出完成挑战的每周分解计划（第 1 周做什么，第 2 周做什么）")
            .rule("明确说明挑战如何与用户的主要目标（" + goal + "）相关联")
            .context("挑战推荐背景",
                "用户训练偏好：" + mode + "\n"
                + "目标体型：" + goal + "\n"
                + "推荐挑战方向：" + challengeRecs + "\n"
                + "当前积分：" + (service.attempts().size() * 50 + service.plans().size() * 20) + " 分"
            )
            .build();
    }

    @Override
    protected String nextActions() {
        return "参加推荐挑战；今日打卡记录；查看挑战排行榜；兑换成就徽章";
    }

    @Override
    protected String usedContext() {
        return "训练偏好、目标体型、历史记录、当前积分";
    }
}
