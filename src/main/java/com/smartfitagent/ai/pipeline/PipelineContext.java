package com.smartfitagent.ai.pipeline;

import com.smartfitagent.model.UserProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Pipeline 上下文对象
 *
 * 在责任链（Chain of Responsibility）的各个处理步骤之间传递状态。
 * 每次 AI 请求创建一个独立的 PipelineContext 实例，贯穿全流程。
 *
 * 生命周期：
 *  原始用户输入
 *    → IntentDetectionStep (填充 detectedIntent, keywords)
 *    → PromptEnrichmentStep (填充 enrichedUserPrompt, enrichmentNotes)
 *    → ContextInjectionStep (填充 finalSystemPrompt)
 *    → SafetyFilterStep    (检查 safetyPassed)
 *    → AI 调用
 *    → ResponsePostProcessStep (填充 processedResponse)
 */
public class PipelineContext {

    // ── Input ─────────────────────────────────────────────────────────────────

    private final String originalUserMessage;
    private final String agentType;
    private UserProfile userProfile;

    // ── Intent detection results ──────────────────────────────────────────────

    private String detectedIntent = "general";
    private List<String> keywords = new ArrayList<>();
    private double intentConfidence = 0.0;
    private Map<String, Double> intentScores = new HashMap<>();

    // ── Prompt enrichment results ─────────────────────────────────────────────

    private String enrichedUserPrompt;
    private List<String> enrichmentNotes = new ArrayList<>();
    private String injectedContext = "";

    // ── System prompt ─────────────────────────────────────────────────────────

    private String finalSystemPrompt;

    // ── Safety ────────────────────────────────────────────────────────────────

    private boolean safetyPassed = true;
    private String safetyBlockReason = "";

    // ── AI Response ───────────────────────────────────────────────────────────

    private String rawAiResponse;
    private String processedResponse;

    // ── Metadata ──────────────────────────────────────────────────────────────

    private String modelUsed = "";
    private long pipelineStartMs = System.currentTimeMillis();
    private final Map<String, Object> metadata = new HashMap<>();
    private final List<String> processingLog = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public PipelineContext(String originalUserMessage, String agentType, UserProfile userProfile) {
        this.originalUserMessage = originalUserMessage;
        this.agentType = agentType;
        this.userProfile = userProfile;
        this.enrichedUserPrompt = originalUserMessage;
        this.processingLog.add("Pipeline 开始: agentType=" + agentType);
    }

    public void log(String stepName, String note) {
        processingLog.add("[" + stepName + "] " + note);
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - pipelineStartMs;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getOriginalUserMessage() { return originalUserMessage; }
    public String getAgentType() { return agentType; }
    public UserProfile getUserProfile() { return userProfile; }
    public void setUserProfile(UserProfile p) { this.userProfile = p; }

    public String getDetectedIntent() { return detectedIntent; }
    public void setDetectedIntent(String intent) { this.detectedIntent = intent; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public double getIntentConfidence() { return intentConfidence; }
    public void setIntentConfidence(double c) { this.intentConfidence = c; }
    public Map<String, Double> getIntentScores() { return intentScores; }
    public void setIntentScores(Map<String, Double> s) { this.intentScores = s; }

    public String getEnrichedUserPrompt() { return enrichedUserPrompt; }
    public void setEnrichedUserPrompt(String p) { this.enrichedUserPrompt = p; }
    public List<String> getEnrichmentNotes() { return enrichmentNotes; }
    public void addEnrichmentNote(String note) { enrichmentNotes.add(note); }
    public String getInjectedContext() { return injectedContext; }
    public void setInjectedContext(String ctx) { this.injectedContext = ctx; }

    public String getFinalSystemPrompt() { return finalSystemPrompt; }
    public void setFinalSystemPrompt(String p) { this.finalSystemPrompt = p; }

    public boolean isSafetyPassed() { return safetyPassed; }
    public void setSafetyPassed(boolean passed) { this.safetyPassed = passed; }
    public String getSafetyBlockReason() { return safetyBlockReason; }
    public void setSafetyBlockReason(String r) { this.safetyBlockReason = r; }

    public String getRawAiResponse() { return rawAiResponse; }
    public void setRawAiResponse(String r) { this.rawAiResponse = r; }
    public String getProcessedResponse() { return processedResponse; }
    public void setProcessedResponse(String r) { this.processedResponse = r; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String m) { this.modelUsed = m; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void putMetadata(String key, Object value) { metadata.put(key, value); }
    public List<String> getProcessingLog() { return processingLog; }

    /**
     * 生成 Pipeline 执行摘要（用于响应附加信息和调试）
     */
    public String summary() {
        return String.format(
            "intent=%s(%.0f%%) | keywords=%d | enriched=%s | safety=%s | model=%s | time=%dms",
            detectedIntent, intentConfidence * 100,
            keywords.size(),
            enrichmentNotes.isEmpty() ? "no" : enrichmentNotes.size() + "项",
            safetyPassed ? "OK" : "BLOCKED",
            modelUsed,
            elapsedMs()
        );
    }

    @Override
    public String toString() {
        return "PipelineContext{intent='" + detectedIntent + "', agent='" + agentType
                + "', keywords=" + keywords.size() + ", safety=" + safetyPassed + "}";
    }
}
