package com.smartfitagent.ai.decorator;

import com.smartfitagent.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存装饰器（Decorator Pattern）
 *
 * 对相同的 (systemPrompt + userPrompt) 组合缓存 AI 响应，
 * 避免重复调用 API，降低费用和延迟。
 *
 * 实现细节：
 *  - 使用 LRU（Least Recently Used）策略，容量限制通过 LinkedHashMap 实现
 *  - 支持 TTL（时间过期），超时条目在下次访问时失效
 *  - 缓存 key 是 (systemPrompt + userPrompt) 的 SHA-256 哈希（防止 key 过长）
 *  - 统计命中率、节省的 API 调用次数
 *
 * 注意：流式回复不缓存（实时性要求高）。
 */
public class CachingAiClientDecorator extends AiClientDecorator {

    private static final Logger log = LoggerFactory.getLogger(CachingAiClientDecorator.class);

    private final int maxSize;
    private final long ttlMillis;
    private final Map<String, CacheEntry> cache;

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    public CachingAiClientDecorator(AiClient delegate, int maxSize, int ttlMinutes) {
        super(delegate);
        this.maxSize = maxSize;
        this.ttlMillis = TimeUnit.MINUTES.toMillis(ttlMinutes);
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                boolean shouldRemove = size() > maxSize;
                if (shouldRemove) evictions.incrementAndGet();
                return shouldRemove;
            }
        };
        log.info("CachingAiClientDecorator 初始化: maxSize={}, ttl={}min", maxSize, ttlMinutes);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String key = buildCacheKey(systemPrompt, userPrompt);

        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry != null && !entry.isExpired(ttlMillis)) {
                hits.incrementAndGet();
                log.debug("缓存命中: key={}", key.substring(0, Math.min(16, key.length())));
                return entry.value;
            }
            if (entry != null) {
                cache.remove(key);
                log.debug("缓存过期，移除: key={}", key.substring(0, Math.min(16, key.length())));
            }
        }

        misses.incrementAndGet();
        log.debug("缓存未命中，调用真实 AI: provider={}", delegate.name());

        String result = delegate.complete(systemPrompt, userPrompt);

        if (result != null && !result.isBlank()) {
            synchronized (cache) {
                cache.put(key, new CacheEntry(result));
            }
            log.debug("缓存写入: key={}, valueLen={}", key.substring(0, Math.min(16, key.length())),
                    result.length());
        }

        return result;
    }

    // ── Cache management ──────────────────────────────────────────────────────

    public synchronized void invalidate(String systemPrompt, String userPrompt) {
        String key = buildCacheKey(systemPrompt, userPrompt);
        cache.remove(key);
    }

    public synchronized void clearAll() {
        int size = cache.size();
        cache.clear();
        log.info("缓存已清空，移除 {} 条记录", size);
    }

    public synchronized int size() {
        return cache.size();
    }

    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public long getEvictions() { return evictions.get(); }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    public String cacheSummary() {
        return String.format(
            "{\"size\":%d,\"maxSize\":%d,\"hits\":%d,\"misses\":%d,"
            + "\"hitRate\":\"%.1f%%\",\"evictions\":%d}",
            size(), maxSize, hits.get(), misses.get(),
            getHitRate() * 100, evictions.get()
        );
    }

    // ── Cache key generation ──────────────────────────────────────────────────

    private String buildCacheKey(String systemPrompt, String userPrompt) {
        String combined = (systemPrompt == null ? "" : systemPrompt)
                + "|||"
                + (userPrompt == null ? "" : userPrompt);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback: use string hashCode
            return String.valueOf(combined.hashCode());
        }
    }

    // ── Cache entry ───────────────────────────────────────────────────────────

    private static class CacheEntry {
        final String value;
        final long createdAt;

        CacheEntry(String value) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }

        long ageMillis() {
            return System.currentTimeMillis() - createdAt;
        }
    }
}
