package com.smartfitagent.ai.decorator;

import com.smartfitagent.ai.AiClient;

/**
 * AiClient 装饰器抽象基类（Decorator Pattern）
 *
 * 允许在不修改原有 AiClient 实现的情况下，通过组合叠加新行为：
 *   日志记录 → CachingAiClientDecorator → LoggingAiClientDecorator → 真实策略
 *
 * 每个装饰器只专注一个横切关注点（Single Responsibility）。
 */
public abstract class AiClientDecorator implements AiClient {

    protected final AiClient delegate;

    protected AiClientDecorator(AiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("装饰器的被装饰对象不能为 null");
        }
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        return delegate.complete(systemPrompt, userPrompt);
    }

    public AiClient getDelegate() { return delegate; }

    /**
     * 向下追溯获取最内层的真实 AiClient（穿透所有装饰器）
     */
    public AiClient unwrap() {
        if (delegate instanceof AiClientDecorator inner) {
            return inner.unwrap();
        }
        return delegate;
    }

    /**
     * 返回完整的装饰器链描述，用于日志和调试
     */
    public String chainDescription() {
        String delegateDesc = (delegate instanceof AiClientDecorator d)
                ? d.chainDescription()
                : delegate.name();
        return getClass().getSimpleName() + " → " + delegateDesc;
    }
}
