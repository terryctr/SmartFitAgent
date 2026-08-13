package com.smartfitagent.spring;

import com.smartfitagent.ai.AiClient;
import com.smartfitagent.ai.MockAiClient;
import com.smartfitagent.ai.decorator.CachingAiClientDecorator;
import com.smartfitagent.ai.decorator.LoggingAiClientDecorator;
import com.smartfitagent.ai.decorator.RateLimitingAiClientDecorator;
import com.smartfitagent.ai.strategy.*;
import com.smartfitagent.db.Database;
import com.smartfitagent.db.FileDatabase;
import com.smartfitagent.db.MySqlDatabase;
import com.smartfitagent.event.LearningEventBus;
import com.smartfitagent.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

/**
 * Spring IoC 核心配置类。
 *
 * 职责：
 *  1. 根据环境变量选择数据库实现（MySQL 或 文件）
 *  2. 根据 AI_PROVIDER 配置选择并装饰 AiClient
 *     - 装饰器顺序：RateLimiting → Caching → Logging → 真实策略
 *  3. 配置异步线程池
 *  4. 注册 LearningEventBus（Observer 模式）
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Autowired
    private AiProperties aiProps;

    @Autowired
    private DataSource dataSource;

    // ── Database ──────────────────────────────────────────────────────────────

    @Bean
    @Primary
    public Database database() {
        String dbMode = System.getenv().getOrDefault("DB_MODE", "file");
        Database db;
        if ("mysql".equalsIgnoreCase(dbMode)) {
            log.info("使用 MySQL 数据库模式");
            db = new MySqlDatabase();
        } else {
            log.info("使用文件数据库模式 (开发/演示)");
            db = new FileDatabase();
        }
        try {
            db.init();
        } catch (Exception e) {
            log.warn("数据库初始化警告: {} — 降级到空库", e.getMessage());
        }
        return db;
    }

    @Bean
    public JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    // ── LLM / AI Client (Strategy + Decorator Pattern) ────────────────────────

    /**
     * 根据 AiProperties 构建 AiClient：
     *  Strategy → 选择 Qwen / DeepSeek / Kimi / Mock
     *  然后依次包装 Logging → Caching → RateLimiting 装饰器
     */
    @Bean
    @Primary
    public AiClient aiClient() {
        AiClient core = buildCoreClient();
        log.info("AI 核心客户端: {}", core.name());

        // 装饰器链 (Decorator Pattern)
        AiClient decorated = new LoggingAiClientDecorator(core);
        if (aiProps.getCache().isEnabled()) {
            decorated = new CachingAiClientDecorator(decorated,
                    aiProps.getCache().getMaxSize(),
                    aiProps.getCache().getExpireAfterWriteMinutes());
            log.info("已启用 AI 响应缓存 (maxSize={}, ttl={}min)",
                    aiProps.getCache().getMaxSize(),
                    aiProps.getCache().getExpireAfterWriteMinutes());
        }
        if (aiProps.getRateLimit().isEnabled()) {
            decorated = new RateLimitingAiClientDecorator(decorated,
                    aiProps.getRateLimit().getRequestsPerMinute());
            log.info("已启用 AI 请求限速 ({} req/min)",
                    aiProps.getRateLimit().getRequestsPerMinute());
        }
        return decorated;
    }

    private AiClient buildCoreClient() {
        String provider = aiProps.resolvedProvider();

        return switch (provider) {
            case "qwen" -> {
                AiProperties.ProviderConfig cfg = aiProps.getQwen();
                String key = cfg.isConfigured() ? cfg.getApiKey() : aiProps.getApiKey();
                if (key == null || key.isBlank()) {
                    log.warn("Qwen API Key 未配置，使用 Mock 客户端");
                    yield new MockAiClient();
                }
                log.info("使用 Qwen 策略: model={}", cfg.getModel());
                yield new QwenStrategy(cfg.getUrl(), key, cfg.getModel());
            }
            case "deepseek" -> {
                AiProperties.ProviderConfig cfg = aiProps.getDeepseek();
                String key = cfg.isConfigured() ? cfg.getApiKey() : aiProps.getApiKey();
                if (key == null || key.isBlank()) {
                    log.warn("DeepSeek API Key 未配置，使用 Mock 客户端");
                    yield new MockAiClient();
                }
                log.info("使用 DeepSeek 策略: model={}", cfg.getModel());
                yield new DeepSeekStrategy(cfg.getUrl(), key, cfg.getModel());
            }
            case "kimi" -> {
                AiProperties.ProviderConfig cfg = aiProps.getKimi();
                String key = cfg.isConfigured() ? cfg.getApiKey() : aiProps.getApiKey();
                if (key == null || key.isBlank()) {
                    log.warn("Kimi API Key 未配置，使用 Mock 客户端");
                    yield new MockAiClient();
                }
                log.info("使用 Kimi 策略: model={}", cfg.getModel());
                yield new KimiStrategy(cfg.getUrl(), key, cfg.getModel());
            }
            case "multi" -> {
                log.info("使用 Multi-Model 策略（轮询多模型）");
                yield buildMultiModelStrategy();
            }
            default -> {
                log.info("使用 Mock AI 客户端（无真实 API 调用）");
                yield new MockAiClient();
            }
        };
    }

    private MultiModelStrategy buildMultiModelStrategy() {
        MultiModelStrategy multi = new MultiModelStrategy();
        if (aiProps.getQwen().isConfigured()) {
            multi.addStrategy(new QwenStrategy(
                    aiProps.getQwen().getUrl(),
                    aiProps.getQwen().getApiKey(),
                    aiProps.getQwen().getModel()));
        }
        if (aiProps.getDeepseek().isConfigured()) {
            multi.addStrategy(new DeepSeekStrategy(
                    aiProps.getDeepseek().getUrl(),
                    aiProps.getDeepseek().getApiKey(),
                    aiProps.getDeepseek().getModel()));
        }
        if (aiProps.getKimi().isConfigured()) {
            multi.addStrategy(new KimiStrategy(
                    aiProps.getKimi().getUrl(),
                    aiProps.getKimi().getApiKey(),
                    aiProps.getKimi().getModel()));
        }
        if (!multi.hasStrategies()) {
            multi.addStrategy(new MockAiClient());
        }
        return multi;
    }

    // ── Event Bus (Observer Pattern) ──────────────────────────────────────────

    @Bean
    public LearningEventBus learningEventBus() {
        return new LearningEventBus();
    }

    // ── Core Services ─────────────────────────────────────────────────────────

    @Bean
    public StudyService studyService(Database database, LearningEventBus eventBus) {
        return new StudyService(database, eventBus);
    }

    // ── Async Executor ────────────────────────────────────────────────────────

    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "dbTaskExecutor")
    public Executor dbTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("db-task-");
        executor.initialize();
        return executor;
    }
}
