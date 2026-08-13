package com.smartfitagent.ai.strategy;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek LLM 策略实现（Strategy Pattern）
 *
 * 对接 DeepSeek API（OpenAI 兼容接口）：
 *   https://api.deepseek.com/chat/completions
 *
 * 特点：
 *  - deepseek-chat：通用对话，性价比高
 *  - deepseek-reasoner (R1)：深度推理，适合复杂训练计划制定
 *  - 支持 prefix caching，多轮对话成本低
 *  - 数学/逻辑推理能力强，适合 BMI 计算和训练量规划
 *
 * 在 AI Pipeline 中的角色：
 *  当 AI_PROVIDER=deepseek 时，被 AppConfig 注入为主 AiClient。
 *  在计划制定类查询中表现最佳（PlannerAgent 的首选）。
 */
public class DeepSeekStrategy extends AbstractLlmStrategy {

    private static final String DEFAULT_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    public DeepSeekStrategy(String url, String apiKey, String model) {
        super(
            (url == null || url.isBlank()) ? DEFAULT_URL : url,
            apiKey,
            (model == null || model.isBlank()) ? DEFAULT_MODEL : model
        );
        log.info("DeepSeekStrategy 初始化: model={}, url={}", this.model, this.url);
    }

    @Override
    public String name() {
        return "deepseek:" + model;
    }

    @Override
    public String displayName() {
        return "DeepSeek (" + model + ")";
    }

    @Override
    public int maxContextTokens() {
        return switch (model) {
            case "deepseek-reasoner", "deepseek-r1" -> 65536;
            default                                  -> 65536;
        };
    }

    @Override
    public double defaultTemperature() {
        // DeepSeek-Reasoner 建议 temperature=1
        return isReasonerModel() ? 1.0 : 0.7;
    }

    @Override
    public double costPer1kTokens() {
        return switch (model) {
            case "deepseek-reasoner", "deepseek-r1" -> 0.0055;
            default                                  -> 0.00014;
        };
    }

    @Override
    public List<String> capabilities() {
        if (isReasonerModel()) {
            return List.of("深度推理", "数学计算", "复杂计划制定", "逻辑分析",
                           "多步骤问题求解", "健身科学分析");
        }
        return List.of("多轮对话", "代码生成", "文本理解", "摘要",
                       "快速响应", "健身问答");
    }

    @Override
    public boolean supportsFunctionCalling() {
        return !isReasonerModel();
    }

    private boolean isReasonerModel() {
        return model.contains("reasoner") || model.contains("r1");
    }

    // ── Request body builders ─────────────────────────────────────────────────

    @Override
    protected String buildRequestBody(String systemPrompt, String userPrompt,
                                       double temperature, boolean stream) {
        StringBuilder sb = new StringBuilder();
        sb.append("{")
          .append("\"model\":").append(q(model)).append(",")
          .append("\"messages\":[");

        if (isReasonerModel()) {
            // R1 模型：system prompt 需合并到 user message（官方建议）
            sb.append("{\"role\":\"user\",\"content\":").append(q(systemPrompt + "\n\n" + userPrompt)).append("}");
        } else {
            sb.append("{\"role\":\"system\",\"content\":").append(q(systemPrompt)).append("},")
              .append("{\"role\":\"user\",\"content\":").append(q(userPrompt)).append("}");
        }

        sb.append("],")
          .append("\"temperature\":").append(temperature).append(",")
          .append("\"max_tokens\":4096,")
          .append("\"stream\":").append(stream);

        // DeepSeek 支持 prefix caching，开启可降低重复 system prompt 成本
        if (!isReasonerModel()) {
            sb.append(",\"top_p\":0.95");
        }

        sb.append("}");
        return sb.toString();
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

    // ── DeepSeek-specific: reasoning chain extraction ─────────────────────────

    /**
     * 对于 DeepSeek-R1，提取推理链（reasoning_content）和最终答案
     * 推理链用于前端展示"AI 思考过程"，增强透明度
     */
    public ReasoningResult completeWithReasoning(String systemPrompt,
                                                  String userPrompt) throws Exception {
        if (!isReasonerModel()) {
            String answer = complete(systemPrompt, userPrompt);
            return new ReasoningResult("", answer);
        }

        String body = buildRequestBody(systemPrompt, userPrompt, defaultTemperature(), false);
        String rawResponse = sendRaw(body);

        String reasoning = extractField(rawResponse, "reasoning_content");
        String answer = extractContent(rawResponse);

        return new ReasoningResult(
            reasoning == null ? "" : reasoning,
            answer == null ? "" : answer
        );
    }

    private String sendRaw(String body) throws Exception {
        var request = buildHttpRequest(body);
        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DeepSeek API 错误: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String extractField(String body, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int index = body.indexOf(marker);
        if (index < 0) return null;
        int afterColon = index + marker.length();
        while (afterColon < body.length() && Character.isWhitespace(body.charAt(afterColon))) {
            afterColon++;
        }
        if (afterColon >= body.length() || body.charAt(afterColon) != '"') return null;
        return unescapeJsonString(body, afterColon + 1);
    }

    /**
     * DeepSeek 推理结果容器
     */
    public record ReasoningResult(String reasoningChain, String finalAnswer) {
        public boolean hasReasoning() {
            return reasoningChain != null && !reasoningChain.isBlank();
        }

        public String format() {
            if (hasReasoning()) {
                return "【推理过程】\n" + reasoningChain + "\n\n【最终答案】\n" + finalAnswer;
            }
            return finalAnswer;
        }
    }
}
