package com.smartfitagent.ai.strategy;

import com.smartfitagent.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 多模型策略（Strategy Pattern 的组合变体）
 *
 * 持有多个 LlmStrategy 实例，支持以下调度模式：
 *  1. 轮询（Round-Robin）— 均匀分配请求，降低单一 API 压力
 *  2. 故障转移（Failover）— 主模型失败时自动切换到备用模型
 *  3. 最优选择（Best-Fit）— 根据查询长度选择上下文最大的模型
 *
 * 用法：AI_PROVIDER=multi 时由 AppConfig 注入。
 */
public class MultiModelStrategy implements LlmStrategy {

    private static final Logger log = LoggerFactory.getLogger(MultiModelStrategy.class);

    public enum SchedulingMode {
        ROUND_ROBIN,
        FAILOVER,
        BEST_FIT
    }

    private final List<LlmStrategy> strategies = new ArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private SchedulingMode mode = SchedulingMode.FAILOVER;

    public MultiModelStrategy() {}

    public MultiModelStrategy(SchedulingMode mode) {
        this.mode = mode;
    }

    public void addStrategy(AiClient client) {
        if (client instanceof LlmStrategy strategy) {
            strategies.add(strategy);
            log.info("MultiModelStrategy 添加策略: {}", strategy.name());
        } else {
            // Wrap non-LlmStrategy AiClients with a simple adapter
            strategies.add(new LlmStrategyAdapter(client));
            log.info("MultiModelStrategy 添加适配客户端: {}", client.name());
        }
    }

    public boolean hasStrategies() {
        return !strategies.isEmpty();
    }

    public void setMode(SchedulingMode mode) {
        this.mode = mode;
    }

    // ── LlmStrategy implementation ────────────────────────────────────────────

    @Override
    public String name() {
        if (strategies.isEmpty()) return "multi:empty";
        return "multi[" + strategies.stream().map(AiClient::name)
                .reduce((a, b) -> a + "," + b).orElse("") + "]";
    }

    @Override
    public String displayName() {
        return "多模型策略 (" + strategies.size() + " 个模型)";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        return switch (mode) {
            case ROUND_ROBIN -> roundRobinComplete(systemPrompt, userPrompt);
            case FAILOVER    -> failoverComplete(systemPrompt, userPrompt);
            case BEST_FIT    -> bestFitComplete(systemPrompt, userPrompt);
        };
    }

    @Override
    public String completions(List<Map<String, String>> messages, double temperature) throws Exception {
        LlmStrategy strategy = selectStrategy(estimateTokens(
                messages.stream().map(m -> m.getOrDefault("content", ""))
                        .reduce("", (a, b) -> a + b)));
        return strategy.completions(messages, temperature);
    }

    @Override
    public void streamComplete(String systemPrompt, String userPrompt,
                               Consumer<String> callback) throws Exception {
        LlmStrategy strategy = selectStrategy(estimateTokens(systemPrompt + userPrompt));
        strategy.streamComplete(systemPrompt, userPrompt, callback);
    }

    @Override
    public int maxContextTokens() {
        return strategies.stream()
                .mapToInt(LlmStrategy::maxContextTokens)
                .max().orElse(8192);
    }

    @Override
    public double defaultTemperature() {
        return 0.7;
    }

    @Override
    public List<String> capabilities() {
        List<String> all = new ArrayList<>();
        strategies.forEach(s -> {
            s.capabilities().forEach(cap -> {
                if (!all.contains(cap)) all.add(cap);
            });
        });
        return all;
    }

    @Override
    public double costPer1kTokens() {
        return strategies.stream()
                .mapToDouble(LlmStrategy::costPer1kTokens)
                .min().orElse(0.01);
    }

    @Override
    public boolean isAvailable() {
        return strategies.stream().anyMatch(LlmStrategy::isAvailable);
    }

    // ── Scheduling implementations ────────────────────────────────────────────

    private String roundRobinComplete(String systemPrompt, String userPrompt) throws Exception {
        if (strategies.isEmpty()) throw new IllegalStateException("无可用 AI 策略");
        int idx = roundRobinIndex.getAndIncrement() % strategies.size();
        LlmStrategy strategy = strategies.get(idx);
        log.debug("轮询选择策略[{}]: {}", idx, strategy.name());
        return strategy.complete(systemPrompt, userPrompt);
    }

    private String failoverComplete(String systemPrompt, String userPrompt) throws Exception {
        Exception lastEx = null;
        for (LlmStrategy strategy : strategies) {
            try {
                log.debug("尝试策略: {}", strategy.name());
                String result = strategy.complete(systemPrompt, userPrompt);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            } catch (Exception e) {
                lastEx = e;
                log.warn("策略 {} 失败，尝试下一个: {}", strategy.name(), e.getMessage());
            }
        }
        throw new IllegalStateException("所有 AI 策略均失败", lastEx);
    }

    private String bestFitComplete(String systemPrompt, String userPrompt) throws Exception {
        int tokenCount = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
        LlmStrategy best = selectStrategy(tokenCount);
        log.debug("最优选择策略 ({}tokens): {}", tokenCount, best.name());
        return best.complete(systemPrompt, userPrompt);
    }

    private LlmStrategy selectStrategy(int requiredTokens) {
        return strategies.stream()
                .filter(s -> s.maxContextTokens() >= requiredTokens)
                .findFirst()
                .orElse(strategies.isEmpty() ? new LlmStrategyAdapter(new com.smartfitagent.ai.MockAiClient())
                        : strategies.get(strategies.size() - 1));
    }

    // ── Inner adapter for non-LlmStrategy AiClients ───────────────────────────

    private static class LlmStrategyAdapter implements LlmStrategy {
        private final AiClient delegate;
        LlmStrategyAdapter(AiClient delegate) { this.delegate = delegate; }

        @Override public String name() { return delegate.name(); }
        @Override public String complete(String sys, String user) throws Exception {
            return delegate.complete(sys, user);
        }
        @Override public String completions(List<Map<String, String>> msgs, double temp) throws Exception {
            if (msgs.isEmpty()) return "";
            Map<String, String> last = msgs.get(msgs.size() - 1);
            String sys = msgs.stream().filter(m -> "system".equals(m.get("role")))
                    .map(m -> m.getOrDefault("content", "")).findFirst().orElse("");
            return delegate.complete(sys, last.getOrDefault("content", ""));
        }
        @Override public void streamComplete(String sys, String user, Consumer<String> cb) throws Exception {
            cb.accept(complete(sys, user));
        }
        @Override public int maxContextTokens() { return 8192; }
        @Override public double defaultTemperature() { return 0.7; }
        @Override public List<String> capabilities() { return List.of("对话"); }
        @Override public double costPer1kTokens() { return 0.0; }
    }
}
