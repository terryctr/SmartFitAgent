package com.smartfitagent.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 配置属性，从 application.yml 的 ai.* 前缀自动绑定。
 * 封装多模型（Qwen / DeepSeek / Kimi）的 URL、Key、Model 参数，
 * 以及 Pipeline 开关和缓存 / 限流配置。
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "mock";
    private String apiKey = "";
    private String model = "";
    private String baseUrl = "";
    private int timeoutSeconds = 60;
    private int maxRetries = 3;

    private ProviderConfig qwen = new ProviderConfig();
    private ProviderConfig deepseek = new ProviderConfig();
    private ProviderConfig kimi = new ProviderConfig();
    private PipelineConfig pipeline = new PipelineConfig();
    private CacheConfig cache = new CacheConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();

    // ── Nested config classes ─────────────────────────────────────────────────

    public static class ProviderConfig {
        private String url = "";
        private String model = "";
        private String apiKey = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "ProviderConfig{url='" + url + "', model='" + model
                    + "', hasKey=" + isConfigured() + "}";
        }
    }

    public static class PipelineConfig {
        private boolean enrichPrompt = true;
        private boolean detectIntent = true;
        private boolean injectContext = true;
        private boolean safetyFilter = true;
        private boolean postProcess = true;

        public boolean isEnrichPrompt() { return enrichPrompt; }
        public void setEnrichPrompt(boolean enrichPrompt) { this.enrichPrompt = enrichPrompt; }
        public boolean isDetectIntent() { return detectIntent; }
        public void setDetectIntent(boolean detectIntent) { this.detectIntent = detectIntent; }
        public boolean isInjectContext() { return injectContext; }
        public void setInjectContext(boolean injectContext) { this.injectContext = injectContext; }
        public boolean isSafetyFilter() { return safetyFilter; }
        public void setSafetyFilter(boolean safetyFilter) { this.safetyFilter = safetyFilter; }
        public boolean isPostProcess() { return postProcess; }
        public void setPostProcess(boolean postProcess) { this.postProcess = postProcess; }
    }

    public static class CacheConfig {
        private boolean enabled = true;
        private int maxSize = 500;
        private int expireAfterWriteMinutes = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public int getExpireAfterWriteMinutes() { return expireAfterWriteMinutes; }
        public void setExpireAfterWriteMinutes(int m) { this.expireAfterWriteMinutes = m; }
    }

    public static class RateLimitConfig {
        private int requestsPerMinute = 60;
        private boolean enabled = true;

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int r) { this.requestsPerMinute = r; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int t) { this.timeoutSeconds = t; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int r) { this.maxRetries = r; }
    public ProviderConfig getQwen() { return qwen; }
    public void setQwen(ProviderConfig qwen) { this.qwen = qwen; }
    public ProviderConfig getDeepseek() { return deepseek; }
    public void setDeepseek(ProviderConfig deepseek) { this.deepseek = deepseek; }
    public ProviderConfig getKimi() { return kimi; }
    public void setKimi(ProviderConfig kimi) { this.kimi = kimi; }
    public PipelineConfig getPipeline() { return pipeline; }
    public void setPipeline(PipelineConfig pipeline) { this.pipeline = pipeline; }
    public CacheConfig getCache() { return cache; }
    public void setCache(CacheConfig cache) { this.cache = cache; }
    public RateLimitConfig getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimitConfig rateLimit) { this.rateLimit = rateLimit; }

    /**
     * 返回当前激活的主 provider 名称（小写）。
     */
    public String resolvedProvider() {
        return provider == null ? "mock" : provider.toLowerCase();
    }

    @Override
    public String toString() {
        return "AiProperties{provider='" + provider + "', qwen=" + qwen
                + ", deepseek=" + deepseek + ", kimi=" + kimi + "}";
    }
}
