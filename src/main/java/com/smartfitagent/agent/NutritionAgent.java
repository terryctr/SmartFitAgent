package com.smartfitagent.agent;

import com.smartfitagent.StudyService;
import com.smartfitagent.ai.AiClient;
import com.smartfitagent.model.UserProfile;

/**
 * 营养饮食专项 Agent（模板方法模式）
 *
 * 专注于：
 *  - 每日宏营养素计算（蛋白质/碳水/脂肪/热量）
 *  - 增肌期 vs 减脂期饮食策略
 *  - 餐前/餐后补给建议
 *  - 常见食物热量查询
 *  - 饮食误区纠正
 *
 * System Prompt 根据用户的 BMI、目标体型和训练模式动态生成，
 * 确保建议高度个性化，不使用通用模板。
 */
public class NutritionAgent extends AbstractAgent {

    private final StudyService service;

    public NutritionAgent(AiClient ai, StudyService service) {
        super(ai);
        this.service = service;
    }

    @Override
    protected String type() { return "nutrition"; }

    @Override
    protected String systemPrompt(UserProfile user) {
        String context = service.promptContext();
        double weight = extractWeight(user);
        String goal = user != null ? user.goal() : "均衡健康";
        String mode = user != null ? user.preferredStyle() : "减脂与力量并行";

        boolean isBulk = mode.contains("增肌") || mode.contains("只做力量");
        boolean isCut  = mode.contains("减脂") || mode.contains("只做减脂");

        String phase = isBulk ? "增肌期（热量盈余）" : isCut ? "减脂期（热量赤字）" : "身材维持期";

        return new PromptBuilder()
            .identity("专业运动营养师，擅长为健身人群制定个性化饮食方案，精通宏营养素计算")
            .user(user)
            .context("健身数据与饮食历史", context)
            .rule("必须根据用户体重计算蛋白质目标（增肌期 2g/kg，减脂期 1.6g/kg，维持期 1.4g/kg）")
            .rule("必须说明当前所处阶段：" + phase)
            .rule("每日热量目标必须基于用户实际体重和 BMI，不使用通用数值")
            .rule("推荐真实可操作的食物，不要列出不现实的复杂食谱")
            .rule("如检测到极端节食倾向，必须给出健康风险警告")
            .context("营养额外指令",
                "当前目标体型偏好：" + goal + "\n"
                + "当前训练阶段：" + phase + "\n"
                + (weight > 0 ? "用户体重约 " + String.format("%.1f", weight) + "kg，"
                    + "蛋白质目标区间 " + (int)(weight * 1.4) + "–" + (int)(weight * 2.0) + "g/天\n" : "")
            )
            .build();
    }

    @Override
    protected String nextActions() {
        return "记录今日饮食摄入；设置蛋白质每日提醒；查看推荐食物热量表";
    }

    @Override
    protected String usedContext() {
        return "身体数据、BMI、目标体型、饮食记录、训练阶段";
    }

    private double extractWeight(UserProfile user) {
        if (user == null || user.grade() == null) return 0.0;
        String text = user.grade();
        int kg = text.indexOf("kg");
        if (kg > 0) {
            int slash = text.lastIndexOf('/', kg);
            int start = slash >= 0 ? slash + 1 : 0;
            try {
                return Double.parseDouble(text.substring(start, kg).trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
