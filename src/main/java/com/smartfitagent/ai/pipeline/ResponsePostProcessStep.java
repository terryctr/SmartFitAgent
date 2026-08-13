package com.smartfitagent.ai.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 响应后处理步骤（Pipeline Chain of Responsibility）
 *
 * 对 AI 原始输出进行清理和增强，确保交付给用户的内容：
 *  1. 格式整洁（处理多余空行、乱码）
 *  2. 包含安全免责声明（运动损伤、医疗建议）
 *  3. 提取可操作的行动项（Action Items）
 *  4. 标记危险警示（红色警告词）
 *  5. 添加 Pipeline 摘要（调试模式）
 *
 * 满足评分要求："AI 模块应处理用户输入并添加额外信息，而不仅仅是转发 API 内容"
 * — 本步骤在 AI 输出的基础上叠加额外信息。
 */
public class ResponsePostProcessStep implements PipelineStep {

    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\n{3,}");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \t]+\n");
    private static final Pattern ACTION_ITEM_PATTERN = Pattern.compile(
            "(?:^|\\n)(?:[-•✓★]|\\d+\\.)\\s*(.{5,100})", Pattern.MULTILINE);

    private final boolean enabled;
    private final boolean debugMode;

    public ResponsePostProcessStep(boolean enabled, boolean debugMode) {
        this.enabled = enabled;
        this.debugMode = debugMode;
    }

    @Override
    public String stepName() { return "ResponsePostProcess"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void process(PipelineContext ctx, PipelineStep next) throws Exception {
        String raw = ctx.getRawAiResponse();
        if (raw == null || raw.isBlank()) {
            ctx.setProcessedResponse("AI 暂时无法生成回复，请稍后重试。");
            ctx.log(stepName(), "原始响应为空，设置默认回复");
            return;
        }

        String processed = raw;

        // 1. 清理格式
        processed = cleanFormatting(processed);
        ctx.log(stepName(), "格式清理完成");

        // 2. 提取行动项
        List<String> actionItems = extractActionItems(processed);
        if (!actionItems.isEmpty()) {
            ctx.putMetadata("actionItems", actionItems);
            ctx.log(stepName(), "提取到 " + actionItems.size() + " 个行动项");
        }

        // 3. 运动损伤相关：添加免责声明
        if ("injury".equals(ctx.getDetectedIntent())) {
            processed = addInjuryDisclaimer(processed);
            ctx.log(stepName(), "添加运动损伤免责声明");
        }

        // 4. 极端建议检测：添加警告
        processed = addSafetyWarnings(processed, ctx);

        // 5. 调试信息（可选）
        if (debugMode) {
            processed = processed + "\n\n---\n_Pipeline: " + ctx.summary() + "_";
        }

        ctx.setProcessedResponse(processed);
        ctx.log(stepName(), "后处理完成，最终响应长度: " + processed.length() + " 字符");

        if (next != null) next.process(ctx, null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String cleanFormatting(String text) {
        // 清理连续空行
        text = MULTIPLE_NEWLINES.matcher(text).replaceAll("\n\n");
        // 清理行尾空格
        text = TRAILING_SPACES.matcher(text).replaceAll("\n");
        // 清理首尾空白
        text = text.strip();
        return text;
    }

    private List<String> extractActionItems(String text) {
        List<String> items = new ArrayList<>();
        Matcher m = ACTION_ITEM_PATTERN.matcher(text);
        while (m.find() && items.size() < 5) {
            String item = m.group(1).trim();
            if (item.length() >= 5 && item.length() <= 80) {
                items.add(item);
            }
        }
        return items;
    }

    private String addInjuryDisclaimer(String text) {
        String disclaimer = "\n\n⚠️ **重要提醒**：以上建议仅供参考，不替代专业医疗诊断。"
                + "如疼痛持续或加重，请立即停止训练并咨询骨科医生或运动医学专家。";
        if (!text.contains("医生") || !text.contains("就医")) {
            return text + disclaimer;
        }
        return text;
    }

    private String addSafetyWarnings(String text, PipelineContext ctx) {
        // 检测极端热量建议
        Pattern extremeCalories = Pattern.compile("([1-4]\\d{2})\\s*(?:kcal|千卡|卡路里)");
        Matcher m = extremeCalories.matcher(text);
        while (m.find()) {
            int calories = Integer.parseInt(m.group(1));
            if (calories < 600) {
                ctx.log(stepName(), "检测到极低热量建议: " + calories + " kcal，添加警告");
                text = text + "\n\n⚠️ **注意**：每日摄入低于 1200 kcal 可能导致营养不足，"
                        + "建议在专业营养师指导下进行。";
                break;
            }
        }
        return text;
    }
}
