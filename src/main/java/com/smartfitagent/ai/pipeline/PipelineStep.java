package com.smartfitagent.ai.pipeline;

/**
 * Pipeline 步骤接口（责任链模式，Chain of Responsibility）
 *
 * 每个步骤专注于单一职责：
 *  - IntentDetectionStep    : 检测用户意图和关键词
 *  - PromptEnrichmentStep   : 根据意图和上下文丰富用户提示词
 *  - ContextInjectionStep   : 注入用户个人数据（BMI、训练记录等）到 system prompt
 *  - SafetyFilterStep       : 过滤不安全或无关内容
 *  - ResponsePostProcessStep: 格式化和增强 AI 回复
 *
 * 调用方式：pipeline.process(context) 逐步执行。
 * 步骤可以：
 *  1. 修改 context 中的字段（主要行为）
 *  2. 调用 next.process(context) 传递控制权
 *  3. 短路（不调用 next），用于安全拦截
 */
public interface PipelineStep {

    /**
     * 处理当前步骤并（可选地）传递给下一步
     *
     * @param context 流转中的 Pipeline 上下文，包含输入、中间结果和输出
     * @param next    下一个步骤（责任链中的后继），null 表示已到链尾
     */
    void process(PipelineContext context, PipelineStep next) throws Exception;

    /**
     * 步骤名称，用于日志和监控
     */
    String stepName();

    /**
     * 该步骤是否已启用（配置驱动，关闭时直接透传给 next）
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 便捷方法：如果本步骤被禁用则直接跳过，否则执行 process
     */
    default void processOrSkip(PipelineContext context, PipelineStep next) throws Exception {
        if (isEnabled()) {
            process(context, next);
        } else if (next != null) {
            next.processOrSkip(context, null);
        }
    }
}
