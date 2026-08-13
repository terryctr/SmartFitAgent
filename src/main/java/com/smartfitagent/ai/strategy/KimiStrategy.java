package com.smartfitagent.ai.strategy;

import java.util.List;
import java.util.Map;

/**
 * Kimi（Moonshot）LLM 策略实现（Strategy Pattern）
 *
 * 对接 Moonshot AI API（OpenAI 兼容接口）：
 *   https://api.moonshot.cn/v1/chat/completions
 *
 * 特点：
 *  - moonshot-v1-8k  : 8K 上下文，快速响应
 *  - moonshot-v1-32k : 32K 上下文，适合长文档分析
 *  - moonshot-v1-128k: 128K 超长上下文，适合营养日志摘要、长期训练历史分析
 *  - 中文流畅度高，安全性设计优秀
 *  - 适合健康/健身类内容（风险提示完善）
 *
 * 在 AI Pipeline 中的角色：
 *  当 AI_PROVIDER=kimi 时，被 AppConfig 注入。
 *  HealthAgent 和 NutritionAgent 默认首选 Kimi（安全合规性好）。
 */
public class KimiStrategy extends AbstractLlmStrategy {

    private static final String DEFAULT_URL = "https://api.moonshot.cn/v1/chat/completions";
    private static final String DEFAULT_MODEL = "moonshot-v1-8k";

    public KimiStrategy(String url, String apiKey, String model) {
        super(
            (url == null || url.isBlank()) ? DEFAULT_URL : url,
            apiKey,
            (model == null || model.isBlank()) ? DEFAULT_MODEL : model
        );
        log.info("KimiStrategy 初始化: model={}, url={}", this.model, this.url);
    }

    @Override
    public String name() {
        return "kimi:" + model;
    }

    @Override
    public String displayName() {
        return "Kimi (" + model + ")";
    }

    @Override
    public int maxContextTokens() {
        return switch (model) {
            case "moonshot-v1-128k" -> 128000;
            case "moonshot-v1-32k"  -> 32000;
            default                 -> 8000;
        };
    }

    @Override
    public double defaultTemperature() {
        return 0.3;  // Kimi 更保守，适合健康类内容
    }

    @Override
    public double costPer1kTokens() {
        return switch (model) {
            case "moonshot-v1-128k" -> 0.06;
            case "moonshot-v1-32k"  -> 0.024;
            default                 -> 0.012;
        };
    }

    @Override
    public List<String> capabilities() {
        return List.of("长文档理解", "健康资讯分析", "营养成分解析",
                       "安全健康建议", "中文流畅", "训练计划审核");
    }

    // ── Kimi-specific context window selection ────────────────────────────────

    /**
     * 根据上下文长度自动选择最佳 Kimi 模型变体
     * 当用户历史数据量大时，自动升级到 32k/128k 版本
     */
    public static String selectModel(int estimatedContextTokens) {
        if (estimatedContextTokens > 30000) return "moonshot-v1-128k";
        if (estimatedContextTokens > 7000)  return "moonshot-v1-32k";
        return "moonshot-v1-8k";
    }

    // ── Request body builders ─────────────────────────────────────────────────

    @Override
    protected String buildRequestBody(String systemPrompt, String userPrompt,
                                       double temperature, boolean stream) {
        return "{"
            + "\"model\":" + q(model) + ","
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":" + q(systemPrompt) + "},"
            + "{\"role\":\"user\",\"content\":" + q(userPrompt) + "}"
            + "],"
            + "\"temperature\":" + temperature + ","
            + "\"max_tokens\":4096,"
            + "\"stream\":" + stream
            + "}";
    }

    @Override
    protected String buildMultiTurnBody(List<Map<String, String>> messages, double temperature) {
        return "{"
            + "\"model\":" + q(model) + ","
            + "\"messages\":" + buildMessagesArray(messages) + ","
            + "\"temperature\":" + temperature + ","
            + "\"max_tokens\":4096,"
            + "\"stream\":false"
            + "}";
    }

    // ── Kimi-specific: file/document analysis ────────────────────────────────

    /**
     * 基于长文本内容进行健身/营养报告分析（利用 Kimi 超长上下文优势）
     *
     * @param documentContent 文档内容（如用户的长期饮食日记、训练记录）
     * @param analysisRequest 分析需求
     * @return 分析结果
     */
    public String analyzeDocument(String documentContent, String analysisRequest) throws Exception {
        // 根据文档长度自动选最佳 Kimi 模型
        int estTokens = estimateTokens(documentContent) + estimateTokens(analysisRequest);
        String bestModel = selectModel(estTokens);

        log.info("Kimi 文档分析: 估算 {}tokens，使用 {}", estTokens, bestModel);

        String systemPrompt = "你是一位专业的健康数据分析师，擅长从用户的训练记录和饮食日志中提炼关键信息，"
                + "识别规律、趋势和改进机会，给出基于数据的科学建议。";
        String userPrompt = "以下是用户的记录文档：\n\n" + documentContent
                + "\n\n分析需求：" + analysisRequest;

        String body = "{"
            + "\"model\":" + q(bestModel) + ","
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":" + q(systemPrompt) + "},"
            + "{\"role\":\"user\",\"content\":" + q(userPrompt) + "}"
            + "],"
            + "\"temperature\":0.3,"
            + "\"max_tokens\":8192,"
            + "\"stream\":false"
            + "}";
        return sendAndExtract(body);
    }

    /**
     * 使用 Kimi 进行安全内容审核（健身建议合规检查）
     * 返回 true 表示内容安全，false 表示包含潜在风险内容
     */
    public boolean contentSafetyCheck(String content) throws Exception {
        String checkPrompt = "请检查以下健身建议是否包含可能对普通用户造成伤害的风险内容，"
                + "如极端节食、过度训练、危险动作、不适当的补剂建议等。"
                + "只回复 SAFE 或 UNSAFE。\n内容：" + truncate(content, 500);
        try {
            String result = complete(
                    "你是健康内容安全审核员，只输出 SAFE 或 UNSAFE",
                    checkPrompt);
            return !result.toUpperCase().contains("UNSAFE");
        } catch (Exception e) {
            log.warn("Kimi 内容安全审核失败，默认放行: {}", e.getMessage());
            return true;
        }
    }
}
