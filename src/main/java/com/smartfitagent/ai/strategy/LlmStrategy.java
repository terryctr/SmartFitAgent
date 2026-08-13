package com.smartfitagent.ai.strategy;

import com.smartfitagent.ai.AiClient;
import java.util.List;
import java.util.Map;

/**
 * LLM 策略接口（Strategy Pattern）
 *
 * 定义所有大语言模型适配器必须实现的契约。
 * 上层代码面向此接口编程，运行时通过 AppConfig / AiProperties
 * 注入具体策略（QwenStrategy / DeepSeekStrategy / KimiStrategy），
 * 无需修改任何调用方代码即可切换模型。
 *
 * 扩展了基础 AiClient，增加了：
 *  - 多轮对话能力 (completions)
 *  - 流式输出能力 (stream)
 *  - 模型元信息查询
 *  - 策略能力描述（用于 UI 展示和日志）
 */
public interface LlmStrategy extends AiClient {

    /**
     * 多轮对话：接受完整消息历史
     *
     * @param messages 消息列表，每条消息含 role (system/user/assistant) 和 content
     * @param temperature 温度参数 0.0-2.0
     * @return AI 回复内容
     */
    String completions(List<Map<String, String>> messages, double temperature) throws Exception;

    /**
     * 流式输出（Server-Sent Events / WebSocket 场景）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入（已经过 Pipeline 增强）
     * @param callback     每接收到一个 token chunk 时的回调
     */
    void streamComplete(String systemPrompt, String userPrompt,
                        java.util.function.Consumer<String> callback) throws Exception;

    /**
     * 返回该策略支持的最大上下文 token 数
     */
    int maxContextTokens();

    /**
     * 返回该策略的默认温度参数
     */
    double defaultTemperature();

    /**
     * 返回该策略支持的能力描述列表（用于前端 UI 展示）
     * 例如：["多轮对话", "长文档处理", "代码生成", "数学推理"]
     */
    List<String> capabilities();

    /**
     * 返回每千 token 的估算成本（USD），用于使用量统计
     */
    double costPer1kTokens();

    /**
     * 估算一段文本的 token 数（近似值）
     * 默认实现：中文字符 ÷ 1.5，英文单词 × 1.3
     */
    default int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        long chineseChars = text.chars()
                .filter(c -> c >= 0x4E00 && c <= 0x9FFF)
                .count();
        long totalChars = text.length();
        long asciiWords = text.split("\\s+").length;
        return (int) (chineseChars / 1.5 + (totalChars - chineseChars) * 0.3 + asciiWords * 0.3);
    }

    /**
     * 是否支持函数调用（Function Calling / Tool Use）
     */
    default boolean supportsFunctionCalling() {
        return false;
    }

    /**
     * 返回策略的显示名称（用于 UI 选择器）
     */
    default String displayName() {
        return name();
    }

    /**
     * 是否为有效策略（API Key 已配置且服务可达）
     */
    default boolean isAvailable() {
        return true;
    }
}
