package com.smartfitagent.ai.decorator;

import com.smartfitagent.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志装饰器（Decorator Pattern）
 *
 * 为每一次 AI 调用记录：
 *  - 调用时间、Provider 名称
 *  - 输入 token 估算、输出长度
 *  - 耗时（毫秒）
 *  - 异常信息（如发生）
 *  - 累计总调用次数和失败次数
 *
 * 不修改任何业务逻辑，纯日志横切关注点。
 */
public class LoggingAiClientDecorator extends AiClientDecorator {

    private static final Logger log = LoggerFactory.getLogger(LoggingAiClientDecorator.class);

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong totalInputChars = new AtomicLong(0);
    private final AtomicLong totalOutputChars = new AtomicLong(0);

    public LoggingAiClientDecorator(AiClient delegate) {
        super(delegate);
        log.info("LoggingAiClientDecorator 包装: {}", delegate.name());
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        long callId = totalCalls.incrementAndGet();
        Instant start = Instant.now();

        int inputLen = (systemPrompt == null ? 0 : systemPrompt.length())
                + (userPrompt == null ? 0 : userPrompt.length());
        totalInputChars.addAndGet(inputLen);

        log.debug("[AI调用#{}] Provider={}, 输入字符数={}, SystemPrompt前50字={}",
                callId, delegate.name(), inputLen,
                truncate(systemPrompt, 50));

        try {
            String result = delegate.complete(systemPrompt, userPrompt);

            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            totalLatencyMs.addAndGet(latencyMs);

            int outputLen = result == null ? 0 : result.length();
            totalOutputChars.addAndGet(outputLen);

            log.info("[AI调用#{}] 成功 | Provider={} | 耗时={}ms | 输入={}chars | 输出={}chars",
                    callId, delegate.name(), latencyMs, inputLen, outputLen);

            if (log.isDebugEnabled()) {
                log.debug("[AI调用#{}] 输出前100字: {}", callId, truncate(result, 100));
            }

            return result;

        } catch (Exception e) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            totalFailures.incrementAndGet();

            log.error("[AI调用#{}] 失败 | Provider={} | 耗时={}ms | 错误: {}",
                    callId, delegate.name(), latencyMs, e.getMessage());

            throw e;
        }
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    public long getTotalCalls() { return totalCalls.get(); }
    public long getTotalFailures() { return totalFailures.get(); }
    public long getTotalSuccesses() { return totalCalls.get() - totalFailures.get(); }

    public double getAverageLatencyMs() {
        long calls = getTotalSuccesses();
        return calls == 0 ? 0.0 : (double) totalLatencyMs.get() / calls;
    }

    public double getSuccessRate() {
        long calls = totalCalls.get();
        return calls == 0 ? 1.0 : (double) getTotalSuccesses() / calls;
    }

    public long getTotalInputChars() { return totalInputChars.get(); }
    public long getTotalOutputChars() { return totalOutputChars.get(); }

    /**
     * 返回统计摘要（用于 /api/ai/stats 端点）
     */
    public String statsSummary() {
        return String.format(
            "{\"provider\":\"%s\",\"totalCalls\":%d,\"failures\":%d,"
            + "\"successRate\":\"%.1f%%\",\"avgLatencyMs\":\"%.0f\","
            + "\"totalInputChars\":%d,\"totalOutputChars\":%d}",
            delegate.name(),
            getTotalCalls(),
            getTotalFailures(),
            getSuccessRate() * 100,
            getAverageLatencyMs(),
            getTotalInputChars(),
            getTotalOutputChars()
        );
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
