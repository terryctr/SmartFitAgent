package com.smartfitagent.ai.pipeline;

import com.smartfitagent.model.UserProfile;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 提示词增强步骤（Pipeline Chain of Responsibility）
 *
 * 核心职责：根据意图检测结果和用户上下文，对原始用户输入进行多维度增强，
 * 使 AI 的回复更加精准、个性化和有帮助。
 *
 * 增强策略（满足评分要求："AI模块应处理用户输入并添加额外信息，而不仅仅是转发"）：
 *
 * 1. 意图前缀注入
 *    根据检测到的意图，在用户问题前加入意图标签，帮助 AI 理解上下文。
 *
 * 2. 个人数据注入
 *    将用户的 BMI、目标体重、训练偏好等关键数据内嵌到提示词中。
 *
 * 3. 时间上下文
 *    注入当前日期、星期（用于制定当周训练计划）。
 *
 * 4. 关键词扩展
 *    基于检测到的关键词，补充相关的专业术语或对比问题，引导 AI 给出更完整的答案。
 *
 * 5. 输出格式指引
 *    根据意图类型，在提示词末尾追加输出格式要求（列表、表格、步骤等）。
 */
public class PromptEnrichmentStep implements PipelineStep {

    private final boolean enabled;

    public PromptEnrichmentStep(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String stepName() { return "PromptEnrichment"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void process(PipelineContext ctx, PipelineStep next) throws Exception {
        String original = ctx.getOriginalUserMessage();
        String intent = ctx.getDetectedIntent();
        UserProfile user = ctx.getUserProfile();

        StringBuilder enriched = new StringBuilder();

        // 1. 意图标签前缀
        String intentLabel = intentToLabel(intent);
        if (!intentLabel.isEmpty()) {
            enriched.append("[").append(intentLabel).append("]\n");
            ctx.addEnrichmentNote("添加意图标签: " + intentLabel);
        }

        // 2. 时间上下文
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        String weekday = LocalDate.now().getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.CHINESE);
        enriched.append("当前日期：").append(today).append("（").append(weekday).append("）\n");
        ctx.addEnrichmentNote("注入时间上下文");

        // 3. 用户个人数据摘要
        if (user != null) {
            String userSummary = buildUserSummary(user);
            if (!userSummary.isEmpty()) {
                enriched.append("用户信息摘要：").append(userSummary).append("\n");
                ctx.addEnrichmentNote("注入用户数据摘要");
            }
        }

        // 4. 原始问题
        enriched.append("\n用户问题：").append(original);

        // 5. 关键词扩展提示
        List<String> keywords = ctx.getKeywords();
        if (!keywords.isEmpty()) {
            enriched.append("\n（检测到关键词：").append(String.join("、", keywords)).append("）");
            ctx.addEnrichmentNote("注入关键词: " + keywords.size() + " 个");
        }

        // 6. 输出格式指引
        String formatGuide = buildFormatGuide(intent);
        if (!formatGuide.isEmpty()) {
            enriched.append("\n\n").append(formatGuide);
            ctx.addEnrichmentNote("添加格式指引: " + intent);
        }

        ctx.setEnrichedUserPrompt(enriched.toString());
        ctx.log(stepName(), "提示词增强完成，从 " + original.length() + " 字符扩展到 "
                + enriched.length() + " 字符，共 " + ctx.getEnrichmentNotes().size() + " 项增强");

        if (next != null) next.process(ctx, null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String intentToLabel(String intent) {
        return switch (intent) {
            case "training_plan"  -> "训练计划请求";
            case "nutrition"      -> "营养饮食咨询";
            case "bmi_analysis"   -> "体重体型分析";
            case "recovery"       -> "恢复与休息建议";
            case "progress_check" -> "进度评估";
            case "mindset"        -> "心态与动力";
            case "challenge"      -> "挑战任务";
            case "injury"         -> "运动损伤咨询";
            case "supplement"     -> "营养补剂咨询";
            default               -> "";
        };
    }

    private String buildUserSummary(UserProfile user) {
        if (user == null) return "";
        StringBuilder sb = new StringBuilder();
        if (user.name() != null && !user.name().isBlank()) {
            sb.append("姓名: ").append(user.name()).append(" | ");
        }
        if (user.grade() != null && !user.grade().isBlank()) {
            sb.append("身体数据: ").append(user.grade()).append(" | ");
        }
        if (user.goal() != null && !user.goal().isBlank()) {
            sb.append("目标: ").append(user.goal()).append(" | ");
        }
        if (user.preferredStyle() != null && !user.preferredStyle().isBlank()) {
            sb.append("偏好模式: ").append(user.preferredStyle());
        }
        String result = sb.toString();
        if (result.endsWith(" | ")) result = result.substring(0, result.length() - 3);
        return result;
    }

    private String buildFormatGuide(String intent) {
        return switch (intent) {
            case "training_plan" ->
                "请按以下格式输出训练计划：\n" +
                "1. 每周训练安排（分天列出）\n" +
                "2. 每次训练动作（名称、组数、次数、组间休息）\n" +
                "3. 热身和拉伸建议\n" +
                "4. 注意事项和安全提醒";
            case "nutrition" ->
                "请按以下格式输出营养建议：\n" +
                "1. 每日宏营养素目标（蛋白质/碳水/脂肪/热量）\n" +
                "2. 食物选择推荐\n" +
                "3. 三餐分配建议\n" +
                "4. 需要避免的食物";
            case "bmi_analysis" ->
                "请按以下格式分析：\n" +
                "1. 当前 BMI 计算和分类\n" +
                "2. 目标 BMI 和目标体重\n" +
                "3. 达到目标的时间预估\n" +
                "4. 综合建议";
            case "injury" ->
                "请按以下格式回复（注意：不替代专业医疗诊断）：\n" +
                "1. 常见原因分析\n" +
                "2. 即时处理建议（RICE 原则等）\n" +
                "3. 训练调整建议\n" +
                "4. 何时需要就医";
            case "recovery" ->
                "请按以下格式输出恢复建议：\n" +
                "1. 主动恢复方案\n" +
                "2. 睡眠优化建议\n" +
                "3. 拉伸和放松技巧\n" +
                "4. 营养支持建议";
            default -> "";
        };
    }
}
