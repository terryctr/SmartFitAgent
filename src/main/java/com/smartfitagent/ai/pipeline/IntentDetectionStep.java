package com.smartfitagent.ai.pipeline;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 意图检测步骤（Pipeline Chain of Responsibility）
 *
 * 基于规则+关键词，将用户输入分类为以下意图之一：
 *  - training_plan  : 制定训练计划
 *  - nutrition      : 营养饮食建议
 *  - bmi_analysis   : BMI 体重分析
 *  - recovery       : 恢复休息
 *  - progress_check : 进度检查
 *  - mindset        : 心理动力
 *  - challenge      : 挑战赛事
 *  - injury         : 受伤处理
 *  - supplement     : 补剂营养品
 *  - general        : 一般询问
 *
 * 检测结果存入 PipelineContext.detectedIntent 和 keywords，
 * 供后续步骤（PromptEnrichmentStep）使用。
 *
 * 不依赖外部 AI 调用，纯本地规则，零延迟。
 */
public class IntentDetectionStep implements PipelineStep {

    private static final Map<String, List<String>> INTENT_KEYWORDS = new LinkedHashMap<>();

    static {
        INTENT_KEYWORDS.put("training_plan", List.of(
            "计划", "训练计划", "健身计划", "workout", "plan",
            "安排", "制定", "课表", "每周", "几次", "几组", "几个"
        ));
        INTENT_KEYWORDS.put("nutrition", List.of(
            "吃", "饮食", "营养", "热量", "蛋白质", "碳水", "脂肪", "卡路里",
            "kcal", "食物", "食谱", "减肥", "增重", "增肌饮食", "节食"
        ));
        INTENT_KEYWORDS.put("bmi_analysis", List.of(
            "bmi", "体重", "身高", "体脂", "胖", "瘦", "标准体重",
            "超重", "肥胖", "理想体重", "目标体重"
        ));
        INTENT_KEYWORDS.put("recovery", List.of(
            "恢复", "休息", "睡眠", "酸痛", "疲劳", "doms", "肌肉酸痛",
            "拉伸", "泡沫轴", "主动恢复", "冰敷", "热敷"
        ));
        INTENT_KEYWORDS.put("progress_check", List.of(
            "进度", "效果", "变化", "对比", "数据", "统计", "分析",
            "有没有效果", "进展", "检查", "评估"
        ));
        INTENT_KEYWORDS.put("mindset", List.of(
            "坚持", "动力", "放弃", "心态", "motivation", "鼓励", "坚持不下去",
            "累了", "焦虑", "压力", "自律", "习惯"
        ));
        INTENT_KEYWORDS.put("challenge", List.of(
            "挑战", "比赛", "challenge", "打卡", "排行", "排名",
            "竞赛", "奖励", "积分", "成就"
        ));
        INTENT_KEYWORDS.put("injury", List.of(
            "受伤", "疼痛", "痛", "拉伤", "扭伤", "受伤后", "受伤了",
            "knee", "腰", "肩膀", "膝盖", "脚踝", "受伤预防"
        ));
        INTENT_KEYWORDS.put("supplement", List.of(
            "蛋白粉", "肌酸", "补剂", "bcaa", "乳清", "预训练",
            "supplement", "维生素", "鱼油", "钙", "镁"
        ));
    }

    private final boolean enabled;

    public IntentDetectionStep(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String stepName() { return "IntentDetection"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void process(PipelineContext ctx, PipelineStep next) throws Exception {
        String message = ctx.getOriginalUserMessage().toLowerCase();
        Map<String, Double> scores = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            String intent = entry.getKey();
            List<String> keywords = entry.getValue();
            double score = 0.0;
            for (String keyword : keywords) {
                if (message.contains(keyword.toLowerCase())) {
                    score += 1.0;
                }
            }
            if (score > 0) {
                scores.put(intent, score / keywords.size());
            }
        }

        String bestIntent = "general";
        double bestScore = 0.0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestIntent = e.getKey();
            }
        }

        ctx.setDetectedIntent(bestIntent);
        ctx.setIntentConfidence(Math.min(bestScore * 3.0, 1.0));
        ctx.setIntentScores(scores);
        ctx.setKeywords(extractKeywords(message));
        ctx.log(stepName(), "检测到意图: " + bestIntent + " (置信度: " + String.format("%.0f%%", ctx.getIntentConfidence() * 100) + ")");

        if (next != null) next.process(ctx, null);
    }

    private List<String> extractKeywords(String message) {
        List<String> found = new ArrayList<>();
        for (List<String> kwList : INTENT_KEYWORDS.values()) {
            for (String kw : kwList) {
                if (message.contains(kw.toLowerCase()) && !found.contains(kw)) {
                    found.add(kw);
                }
            }
        }
        // Extract numbers (weights, reps, etc.)
        Pattern numPattern = Pattern.compile("\\d+\\.?\\d*\\s*(?:kg|磅|斤|组|个|次|分钟|小时|天|周|kcal|千卡)");
        var matcher = numPattern.matcher(message);
        while (matcher.find()) {
            found.add(matcher.group().trim());
        }
        return found.stream().distinct().limit(10).toList();
    }
}
