package com.smartfitagent.agent;

import com.smartfitagent.StudyService;
import com.smartfitagent.ai.AiClient;
import com.smartfitagent.model.UserProfile;

/**
 * 恢复与休息专项 Agent（模板方法模式）
 *
 * 专注于：
 *  - 运动后恢复策略（主动恢复 vs 被动休息）
 *  - DOMS（延迟性肌肉酸痛）管理
 *  - 睡眠质量优化（睡前习惯、睡眠时长）
 *  - 轻伤处理建议（RICE 原则）
 *  - 过度训练综合征识别和预防
 *  - 超量恢复（Supercompensation）原理应用
 */
public class RecoveryAgent extends AbstractAgent {

    private final StudyService service;

    public RecoveryAgent(AiClient ai, StudyService service) {
        super(ai);
        this.service = service;
    }

    @Override
    protected String type() { return "recovery"; }

    @Override
    protected String systemPrompt(UserProfile user) {
        String context = service.promptContext();
        int planCount = service.plans().size();
        int assessmentCount = service.attempts().size();

        String trainingLoad = "未知";
        if (planCount > 10) trainingLoad = "高训练量";
        else if (planCount > 5) trainingLoad = "中等训练量";
        else if (planCount > 0) trainingLoad = "低训练量";

        return new PromptBuilder()
            .identity("运动恢复专家，擅长帮助健身人群优化训练间恢复，预防过度训练")
            .user(user)
            .context("训练历史与评估数据", context)
            .rule("根据用户训练量(" + trainingLoad + ")给出匹配的恢复策略")
            .rule("区分主动恢复和被动恢复，并说明各自适用场景")
            .rule("睡眠建议必须具体：时长、入睡时间、睡前习惯")
            .rule("如涉及受伤，必须建议就医，不替代专业诊断")
            .rule("提供具体的拉伸动作名称和持续时间")
            .context("恢复背景",
                "用户当前已记录训练计划 " + planCount + " 个，"
                + "体能评估 " + assessmentCount + " 次。\n"
                + "当前训练负荷评估：" + trainingLoad + "\n"
                + "重点关注：睡眠质量、肌肉酸痛管理、下次训练准备。"
            )
            .build();
    }

    @Override
    protected String nextActions() {
        return "记录今日睡眠质量；设置恢复日计划；评估过度训练风险";
    }

    @Override
    protected String usedContext() {
        return "训练计划频率、体能评估记录、训练强度估算";
    }
}
