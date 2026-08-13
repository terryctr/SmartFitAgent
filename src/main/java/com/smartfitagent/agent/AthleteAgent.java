package com.smartfitagent.agent;

import com.smartfitagent.StudyService;
import com.smartfitagent.ai.AiClient;
import com.smartfitagent.model.UserProfile;

/**
 * 运动表现提升 Agent
 *
 * 专注于运动员级别的表现优化：
 * 爆发力、速度、灵活性、专项体能。
 */
public class AthleteAgent extends AbstractAgent {

    private final StudyService service;

    public AthleteAgent(AiClient ai, StudyService service) {
        super(ai);
        this.service = service;
    }

    @Override
    protected String type() { return "athlete"; }

    @Override
    protected String systemPrompt(UserProfile user) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业运动表现教练，专注于提升运动员和运动爱好者的综合体能表现。\n");
        sb.append("你的专业领域包括：爆发力训练、速度敏捷、灵活性与移动性、专项体能、竞技状态调整。\n\n");

        if (user != null) {
            sb.append("【运动员档案】\n");
            sb.append("体重: ").append(user.weightKg()).append("kg，");
            sb.append("身高: ").append(user.heightCm()).append("cm\n");
            sb.append("目标: ").append(user.goal()).append("\n\n");
        }

        sb.append("【指导原则】\n");
        sb.append("1. 周期化训练：赛前、赛中、赛后不同阶段调整重点\n");
        sb.append("2. 专项体能：针对具体运动项目优化训练内容\n");
        sb.append("3. 伤病预防：充分的热身、拉伸和关节稳定性训练\n");
        sb.append("4. 神经肌肉激活：增强运动单元募集效率\n");
        sb.append("5. 速度-力量曲线：根据运动需求定位在力量-速度频谱的最优位置\n\n");

        sb.append("请结合科学训练方法，为用户提供专业的运动表现提升建议。");
        return sb.toString();
    }

    @Override
    protected String nextActions() {
        return "制定专项训练计划 | 评估爆发力水平 | 查看移动性测试";
    }

    @Override
    protected String usedContext() {
        return "用户体型数据 + 运动表现训练方法论";
    }
}
