package com.smartfitagent.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Spring Boot 主入口类 — AI 健身学习助手平台
 *
 * 采用 MVC 架构，整合以下核心能力：
 *   - 策略模式  : Qwen / DeepSeek / Kimi 多模型自由切换
 *   - 工厂模式  : AiClientFactory / AgentFactory 统一创建入口
 *   - 观察者模式: LearningEventBus 解耦事件驱动流程
 *   - 装饰器模式: CachingAiClientDecorator / LoggingAiClientDecorator
 *   - 责任链模式: AiPipeline 分步骤增强用户输入
 *   - 模板方法  : AbstractAgent 定义 AI 响应骨架
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.smartfitagent")
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties
public class SpringApp {

    private static final Logger log = LoggerFactory.getLogger(SpringApp.class);

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")));
        ConfigurableApplicationContext ctx = SpringApplication.run(SpringApp.class, args);
        String port = ctx.getEnvironment().getProperty("server.port", "8080");
        log.info("==========================================================");
        log.info("  SmartFitAgent 已启动: http://127.0.0.1:{}", port);
        log.info("  H2 控制台:             http://127.0.0.1:{}/h2-console", port);
        log.info("  API 健康检查:          http://127.0.0.1:{}/api/health", port);
        log.info("  Spring Actuator:       http://127.0.0.1:{}/actuator/health", port);
        log.info("==========================================================");
    }

    @PostConstruct
    public void init() {
        log.info("SmartFitAgent 初始化完成 — AI 增强健身学习平台");
    }

    /**
     * 全局 CORS 配置，允许前端跨域访问 API
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}
