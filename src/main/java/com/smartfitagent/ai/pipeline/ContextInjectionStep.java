package com.smartfitagent.ai.pipeline;

import com.smartfitagent.StudyService;
import com.smartfitagent.model.UserProfile;

/**
 * 上下文注入步骤（Pipeline Chain of Responsibility）
 *
 * 将用户的完整健身数据（训练计划、饮食记录、体能评估）注入到 system prompt，
 * 确保 AI 在回答时拥有完整的用户上下文，不会凭空捏造用户数据。
 *
 * 注入的信息来自 StudyService.promptContext()，包括：
 *  - 用户档案（身高、体重、目标、训练偏好）
 *  - 实时计算的 BMI / 目标 BMI / 每周训练量
 *  - 已记录的训练计划
 *  - 已记录的饮食摄入笔记
 *  - 已完成的体能评估记录
 *
 * 根据意图类型进行选择性注入，避免 system prompt 过长：
 *  - nutrition 意图 → 重点注入饮食记录
 *  - training_plan 意图 → 重点注入训练计划
 *  - bmi_analysis 意图 → 注入全部数据
 */
public class ContextInjectionStep implements PipelineStep {

    private final StudyService studyService;
    private final String baseSystemPrompt;
    private final boolean enabled;

    public ContextInjectionStep(StudyService studyService, String baseSystemPrompt, boolean enabled) {
        this.studyService = studyService;
        this.baseSystemPrompt = baseSystemPrompt;
        this.enabled = enabled;
    }

    @Override
    public String stepName() { return "ContextInjection"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void process(PipelineContext ctx, PipelineStep next) throws Exception {
        StringBuilder systemPrompt = new StringBuilder(baseSystemPrompt);

        String intent = ctx.getDetectedIntent();
        UserProfile user = ctx.getUserProfile();

        // 全量上下文
        String fullContext = studyService.promptContext();
        if (!fullContext.isBlank()) {
            systemPrompt.append("\n\n【用户数据上下文】\n").append(fullContext);
            ctx.setInjectedContext(fullContext);
            ctx.log(stepName(), "注入完整用户上下文，字符数: " + fullContext.length());
        }

        // 意图特定提醒
        String intentReminder = buildIntentReminder(intent, user);
        if (!intentReminder.isEmpty()) {
            systemPrompt.append("\n\n【本次请求特别注意事项】\n").append(intentReminder);
            ctx.log(stepName(), "注入意图特定提醒: " + intent);
        }

        // 安全和责任声明
        systemPrompt.append("\n\n【安全声明】\n")
                .append("所有建议仅供参考，不替代专业医疗或运动科学指导。")
                .append("如用户有特定健康问题，建议咨询医生或专业教练。");

        ctx.setFinalSystemPrompt(systemPrompt.toString());
        ctx.log(stepName(), "System Prompt 最终长度: " + systemPrompt.length() + " 字符");

        if (next != null) next.process(ctx, null);
    }

    private String buildIntentReminder(String intent, UserProfile user) {
        String userName = (user != null && user.name() != null) ? user.name() : "用户";
        return switch (intent) {
            case "training_plan" ->
                "制定训练计划时必须参考用户实际的身体数据和训练历史，不要使用通用模板。"
                + "确保训练量（组数×次数）符合用户当前体能水平。"
                + "明确说明哪些动作适合 " + userName + " 的目标身材和当前 BMI。";
            case "nutrition" ->
                "给出的热量和宏营养素目标必须基于用户的体重和目标。"
                + "不要推荐极端节食（日摄入低于基础代谢率）。"
                + "结合用户已记录的饮食摄入数据给出调整建议。";
            case "bmi_analysis" ->
                "必须用用户填写的实际身高体重计算 BMI，不要使用假设数据。"
                + "BMI 分析后给出具体的目标体重范围和达成路径。";
            case "injury" ->
                "运动损伤建议必须保守，优先推荐休息和就医。"
                + "不要给出可能加重损伤的训练建议。"
                + "明确说明哪些症状需要立即停止运动并就医。";
            case "mindset" ->
                "保持积极正向的语气，基于 " + userName + " 的实际进度给出鼓励。"
                + "避免空洞的励志口号，用具体数据说明进步。";
            default -> "";
        };
    }
}
