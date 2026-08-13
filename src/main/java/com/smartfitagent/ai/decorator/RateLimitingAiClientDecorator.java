package com.smartfitagent.ai.decorator;

import com.smartfitagent.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 速率限制装饰器（Decorator Pattern）
 *
 * 基于滑动窗口算法限制每分钟 AI 请求数量，防止：
 *  - API 超出配额限制（429 Too Many Requests）
 *  - 恶意或异常的高频调用
 *  - 单用户消耗过多 token 预算
 *
 * 算法：滑动时间窗口（Sliding Window）
 *  - 维护一个时间戳队列，记录过去 60 秒内的请求时间
 *  - 每次请求前清理过期时间戳，检查剩余配额
 *  - 超过限制时抛出 RateLimitExceededException
 */
public class RateLimitingAiClientDecorator extends AiClientDecorator {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingAiClientDecorator.class);

    private final int requestsPerMinute;
    private final long windowMillis = TimeUnit.MINUTES.toMillis(1);
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();

    private final AtomicLong throttledCount = new AtomicLong(0);
    private final AtomicLong totalAllowed = new AtomicLong(0);

    public RateLimitingAiClientDecorator(AiClient delegate, int requestsPerMinute) {
        super(delegate);
        this.requestsPerMinute = requestsPerMinute;
        log.info("RateLimitingAiClientDecorator 初始化: {}次/分钟", requestsPerMinute);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        checkRateLimit();
        totalAllowed.incrementAndGet();
        return delegate.complete(systemPrompt, userPrompt);
    }

    private synchronized void checkRateLimit() {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;

        // 移除窗口外的旧时间戳
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < windowStart) {
            requestTimestamps.pollFirst();
        }

        if (requestTimestamps.size() >= requestsPerMinute) {
            throttledCount.incrementAndGet();
            long oldestInWindow = requestTimestamps.peekFirst();
            long waitMs = oldestInWindow + windowMillis - now;
            log.warn("AI 请求速率超限 ({}/分钟)，当前窗口已有 {} 请求，需等待 {}ms",
                    requestsPerMinute, requestTimestamps.size(), waitMs);
            throw new RateLimitExceededException(
                    "AI 请求过于频繁，请 " + (waitMs / 1000 + 1) + " 秒后重试",
                    waitMs);
        }

        requestTimestamps.addLast(now);
        log.debug("速率限制检查通过: 当前窗口 {}/{} 请求", requestTimestamps.size(), requestsPerMinute);
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    public long getThrottledCount() { return throttledCount.get(); }
    public long getTotalAllowed() { return totalAllowed.get(); }

    public synchronized int getCurrentWindowCount() {
        long windowStart = System.currentTimeMillis() - windowMillis;
        return (int) requestTimestamps.stream().filter(t -> t >= windowStart).count();
    }

    public int getRemainingInCurrentWindow() {
        return Math.max(0, requestsPerMinute - getCurrentWindowCount());
    }

    public String rateLimitSummary() {
        return String.format(
            "{\"requestsPerMinute\":%d,\"currentWindowCount\":%d,"
            + "\"remaining\":%d,\"throttledCount\":%d,\"totalAllowed\":%d}",
            requestsPerMinute, getCurrentWindowCount(),
            getRemainingInCurrentWindow(), throttledCount.get(), totalAllowed.get()
        );
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    public static class RateLimitExceededException extends RuntimeException {
        private final long retryAfterMs;

        public RateLimitExceededException(String message, long retryAfterMs) {
            super(message);
            this.retryAfterMs = retryAfterMs;
        }

        public long getRetryAfterMs() { return retryAfterMs; }
        public long getRetryAfterSeconds() { return retryAfterMs / 1000 + 1; }
    }
}
