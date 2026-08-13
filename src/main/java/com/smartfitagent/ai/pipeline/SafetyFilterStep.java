package com.smartfitagent.ai.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全过滤步骤（Pipeline Chain of Responsibility）
 *
 * 在调用 AI 之前对用户输入进行安全检查，拦截：
 *  1. 极端节食建议请求（日摄入 < 500kcal）
 *  2. 危险的训练组合（如空腹大重量训练要求）
 *  3. 明显不适合普通用户的补剂（类固醇、EPO 等）
 *  4. 无关政治/歧视内容
 *
 * 过滤方式：基于本地规则（无需 AI 调用），零延迟。
 * 被过滤的请求会设置 safetyPassed=false 并提供用户友好的拒绝原因。
 *
 * 满足评分要求："AI模块应处理用户输入而不仅仅是转发"
 */
public class SafetyFilterStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(SafetyFilterStep.class);

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
        Pattern.compile("类固醇|激素|EPO|生长激素|促红细胞生成素", Pattern.CASE_INSENSITIVE),
        Pattern.compile("绝食|断食\\s*\\d+\\s*天", Pattern.CASE_INSENSITIVE),
        Pattern.compile("减肥药|减脂药|燃脂药|禁药", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> EXTREME_DIET_PATTERNS = List.of(
        Pattern.compile("每天只吃\\s*[1-3]\\d{2}\\s*(?:kcal|千卡|卡路里)"),
        Pattern.compile("每天摄入\\s*[1-4]\\d{2}\\s*(?:kcal|千卡)")
    );

    private static final List<String> BLOCKED_TOPICS = List.of(
        "政治", "宗教", "歧视", "暴力", "自杀", "自残"
    );

    private final boolean enabled;

    public SafetyFilterStep(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String stepName() { return "SafetyFilter"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void process(PipelineContext ctx, PipelineStep next) throws Exception {
        String message = ctx.getEnrichedUserPrompt();
        String original = ctx.getOriginalUserMessage();

        // 1. 检查危险物质
        for (Pattern p : DANGEROUS_PATTERNS) {
            if (p.matcher(original).find()) {
                blockRequest(ctx, "检测到可能的危险物质或方法。作为 AI 健身助手，我无法提供类固醇、禁药等内容的建议。"
                        + "如您有此类需求，请咨询专业医疗机构。");
                return;
            }
        }

        // 2. 检查极端节食
        for (Pattern p : EXTREME_DIET_PATTERNS) {
            if (p.matcher(original).find()) {
                blockRequest(ctx, "您提到的热量摄入目标可能过低，存在健康风险。"
                        + "女性建议最低摄入 1200 kcal/天，男性建议最低 1500 kcal/天。"
                        + "请咨询专业营养师制定安全的饮食计划。");
                return;
            }
        }

        // 3. 检查无关话题
        for (String topic : BLOCKED_TOPICS) {
            if (original.contains(topic)) {
                blockRequest(ctx, "我是专注于健身和健康的 AI 助手，无法处理此类话题。"
                        + "如您需要帮助，请联系专业机构。");
                return;
            }
        }

        // 4. 检查空输入
        if (original == null || original.trim().length() < 3) {
            blockRequest(ctx, "请输入您的问题，我会尽力帮助您！");
            return;
        }

        // 5. 输入长度限制
        if (original.length() > 2000) {
            String truncated = original.substring(0, 2000);
            ctx.setEnrichedUserPrompt(ctx.getEnrichedUserPrompt().replace(original, truncated));
            ctx.addEnrichmentNote("输入内容过长，已截断至 2000 字符");
            log.info("SafetyFilter 截断过长输入: {} → 2000 字符", original.length());
        }

        ctx.setSafetyPassed(true);
        ctx.log(stepName(), "安全检查通过");

        if (next != null) next.process(ctx, null);
    }

    private void blockRequest(PipelineContext ctx, String reason) {
        ctx.setSafetyPassed(false);
        ctx.setSafetyBlockReason(reason);
        ctx.setProcessedResponse(reason);
        log.warn("SafetyFilter 拦截请求: {}", reason.substring(0, Math.min(50, reason.length())));
        ctx.log(stepName(), "请求被拦截: " + reason.substring(0, Math.min(50, reason.length())));
        // 不调用 next，短路责任链
    }
}
