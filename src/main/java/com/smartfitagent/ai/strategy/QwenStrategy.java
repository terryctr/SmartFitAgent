package com.smartfitagent.ai.strategy;

import java.util.List;
import java.util.Map;

/**
 * 通义千问（Qwen）LLM 策略实现（Strategy Pattern）
 *
 * 对接阿里云灵积（DashScope）OpenAI 兼容接口：
 *   https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 *
 * 特点：
 *  - 支持 qwen-plus / qwen-max / qwen-turbo 等系列模型
 *  - 上下文窗口最大 128K tokens（qwen-max）
 *  - 中文理解能力优秀，适合健身领域中文专业术语处理
 *  - 支持 Function Calling（工具调用）
 *
 * 在 AI Pipeline 中的角色：
 *  当 AI_PROVIDER=qwen 时，本策略被 AppConfig 注入为主 AiClient，
 *  在 PromptEnrichmentStep 增强后的 prompt 上调用 complete() 方法。
 */
public class QwenStrategy extends AbstractLlmStrategy {

    private static final String DEFAULT_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_MODEL = "qwen-plus";

    public QwenStrategy(String url, String apiKey, String model) {
        super(
            (url == null || url.isBlank()) ? DEFAULT_URL : url,
            apiKey,
            (model == null || model.isBlank()) ? DEFAULT_MODEL : model
        );
        log.info("QwenStrategy 初始化: model={}, url={}", this.model, this.url);
    }

    @Override
    public String name() {
        return "qwen:" + model;
    }

    @Override
    public String displayName() {
        return "通义千问 (" + model + ")";
    }

    @Override
    public int maxContextTokens() {
        return switch (model) {
            case "qwen-max", "qwen-max-latest"         -> 128000;
            case "qwen-plus", "qwen-plus-latest"       -> 131072;
            case "qwen-turbo", "qwen-turbo-latest"     -> 1000000;
            case "qwen-long"                           -> 10000000;
            default                                    -> 32768;
        };
    }

    @Override
    public double defaultTemperature() {
        return 0.7;
    }

    @Override
    public double costPer1kTokens() {
        return switch (model) {
            case "qwen-max", "qwen-max-latest"     -> 0.04;
            case "qwen-plus", "qwen-plus-latest"   -> 0.008;
            case "qwen-turbo", "qwen-turbo-latest" -> 0.003;
            default                                -> 0.01;
        };
    }

    @Override
    public List<String> capabilities() {
        return List.of("多轮对话", "中文优化", "长文档处理", "代码生成",
                       "数学推理", "工具调用", "健身知识问答");
    }

    @Override
    public boolean supportsFunctionCalling() {
        return true;
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
            + "\"stream\":" + stream + ","
            + "\"top_p\":0.8"
            + "}";
    }

    @Override
    protected String buildMultiTurnBody(List<Map<String, String>> messages, double temperature) {
        return "{"
            + "\"model\":" + q(model) + ","
            + "\"messages\":" + buildMessagesArray(messages) + ","
            + "\"temperature\":" + temperature + ","
            + "\"max_tokens\":4096,"
            + "\"stream\":false,"
            + "\"top_p\":0.8"
            + "}";
    }

    // ── Qwen-specific: enable search grounding (联网搜索) ─────────────────────

    /**
     * 启用联网搜索增强的完成请求（Qwen 特有能力）
     * 用于需要实时健身/营养资讯的查询
     */
    public String completeWithWebSearch(String systemPrompt, String userPrompt) throws Exception {
        String body = "{"
            + "\"model\":" + q(model) + ","
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":" + q(systemPrompt) + "},"
            + "{\"role\":\"user\",\"content\":" + q(userPrompt) + "}"
            + "],"
            + "\"temperature\":" + defaultTemperature() + ","
            + "\"max_tokens\":4096,"
            + "\"stream\":false,"
            + "\"tools\":[{\"type\":\"web_search\",\"web_search\":{\"search_query\":" + q(userPrompt) + "}}]"
            + "}";
        log.debug("Qwen 联网搜索请求: {}", truncate(userPrompt, 100));
        return sendAndExtract(body);
    }

    /**
     * 使用 Qwen 进行文本分类，返回分类标签
     * 用于 IntentDetectionStep 检测用户意图
     */
    public String classify(String text, List<String> categories) throws Exception {
        String categoryList = String.join("、", categories);
        String classifyPrompt = "请将以下用户输入分类到最合适的类别。\n"
                + "可选类别：" + categoryList + "\n"
                + "只返回类别名称，不要解释。\n"
                + "用户输入：" + text;
        String body = "{"
            + "\"model\":" + q(model) + ","
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":\"你是一个精确的文本分类助手\"},"
            + "{\"role\":\"user\",\"content\":" + q(classifyPrompt) + "}"
            + "],"
            + "\"temperature\":0.1,"
            + "\"max_tokens\":50,"
            + "\"stream\":false"
            + "}";
        return sendAndExtract(body);
    }

    /**
     * 使用 Qwen 生成文本摘要（用于长消息的缓存 key 生成）
     */
    public String summarize(String text, int maxWords) throws Exception {
        String summaryPrompt = "请用不超过" + maxWords + "个字概括以下内容的核心主题：\n" + text;
        String body = "{"
            + "\"model\":\"qwen-turbo\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":" + q(summaryPrompt) + "}"
            + "],"
            + "\"temperature\":0.3,"
            + "\"max_tokens\":100,"
            + "\"stream\":false"
            + "}";
        return sendAndExtract(body);
    }
}
