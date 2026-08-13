package com.smartfitagent.util;

import java.util.*;

/**
 * 训练推荐工具类
 *
 * 提供基于 BMI、目标体型、训练频率的动态训练计划生成器。
 * 包含常见动作数据库（约80个动作），支持按肌群和难度筛选。
 *
 * 职责：补充 AI Agent 的训练知识库，确保推荐的动作
 * 是真实可行的（而不是 AI 可能虚构的动作）。
 */
public final class WorkoutRecommender {

    private WorkoutRecommender() {}

    public enum MuscleGroup {
        CHEST("胸部"), BACK("背部"), SHOULDERS("肩部"),
        ARMS("手臂"), LEGS("腿部"), CORE("核心"),
        GLUTES("臀部"), FULL_BODY("全身");

        private final String label;
        MuscleGroup(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

    // ── Exercise database ─────────────────────────────────────────────────────

    public record Exercise(String name, MuscleGroup group, Difficulty level,
                           String equipment, String cues, int restSeconds) {
        public String toJson() {
            return String.format(
                "{\"name\":\"%s\",\"group\":\"%s\",\"level\":\"%s\","
                + "\"equipment\":\"%s\",\"cues\":\"%s\",\"rest\":%d}",
                name, group.getLabel(), level.name(), equipment,
                cues.replace("\"", "'"), restSeconds
            );
        }
    }

    public static List<Exercise> allExercises() {
        List<Exercise> db = new ArrayList<>();

        // CHEST
        db.add(new Exercise("俯卧撑", MuscleGroup.CHEST, Difficulty.BEGINNER,
            "徒手", "肘部45°，胸部触地，全程腹部收紧", 60));
        db.add(new Exercise("哑铃卧推", MuscleGroup.CHEST, Difficulty.INTERMEDIATE,
            "哑铃+长凳", "肩胛内收下沉，弧线路径推起", 90));
        db.add(new Exercise("杠铃卧推", MuscleGroup.CHEST, Difficulty.INTERMEDIATE,
            "杠铃+卧推台", "肩胛夹紧，下降到胸部轻触", 120));
        db.add(new Exercise("飞鸟", MuscleGroup.CHEST, Difficulty.INTERMEDIATE,
            "哑铃", "肘部微曲，弧线抱树运动轨迹", 75));
        db.add(new Exercise("上斜俯卧撑", MuscleGroup.CHEST, Difficulty.BEGINNER,
            "徒手+台阶", "双手高于身体，强化上胸", 60));
        db.add(new Exercise("下斜哑铃卧推", MuscleGroup.CHEST, Difficulty.ADVANCED,
            "哑铃+下斜凳", "强化下胸线条，控制下降速度", 90));

        // BACK
        db.add(new Exercise("引体向上", MuscleGroup.BACK, Difficulty.INTERMEDIATE,
            "单杠", "肩胛骨向下向内，专注背阔肌发力", 120));
        db.add(new Exercise("哑铃划船", MuscleGroup.BACK, Difficulty.BEGINNER,
            "哑铃+长凳", "背部平行地面，肘部拉过躯干", 75));
        db.add(new Exercise("坐姿绳索划船", MuscleGroup.BACK, Difficulty.BEGINNER,
            "绳索机", "胸部轻触靠垫，肩胛主动夹紧", 75));
        db.add(new Exercise("杠铃硬拉", MuscleGroup.BACK, Difficulty.ADVANCED,
            "杠铃", "中立脊柱，髋铰链驱动，杠铃贴腿", 180));
        db.add(new Exercise("罗马尼亚硬拉", MuscleGroup.BACK, Difficulty.INTERMEDIATE,
            "哑铃/杠铃", "软腿，感受腘绳肌拉伸，不弓背", 120));
        db.add(new Exercise("高位下拉", MuscleGroup.BACK, Difficulty.BEGINNER,
            "高位下拉机", "握距宽于肩，拉至上胸，肩胛下沉", 90));

        // SHOULDERS
        db.add(new Exercise("哑铃推举", MuscleGroup.SHOULDERS, Difficulty.INTERMEDIATE,
            "哑铃", "肘部90°起，推举至头顶，核心收紧", 90));
        db.add(new Exercise("侧平举", MuscleGroup.SHOULDERS, Difficulty.BEGINNER,
            "哑铃", "小拇指略高，举至与肩平，不借力", 75));
        db.add(new Exercise("前平举", MuscleGroup.SHOULDERS, Difficulty.BEGINNER,
            "哑铃", "举至肩高，控制下降速度", 60));
        db.add(new Exercise("俯身飞鸟", MuscleGroup.SHOULDERS, Difficulty.INTERMEDIATE,
            "哑铃", "上身前倾45°，强化三角肌后束", 75));
        db.add(new Exercise("面拉", MuscleGroup.SHOULDERS, Difficulty.BEGINNER,
            "绳索", "肘高过肩，拇指指向耳后", 60));

        // ARMS
        db.add(new Exercise("哑铃弯举", MuscleGroup.ARMS, Difficulty.BEGINNER,
            "哑铃", "肘部固定，峰值收缩时翻腕", 60));
        db.add(new Exercise("锤式弯举", MuscleGroup.ARMS, Difficulty.BEGINNER,
            "哑铃", "中立握法，强化肱肌和肱桡肌", 60));
        db.add(new Exercise("绳索三头肌下压", MuscleGroup.ARMS, Difficulty.BEGINNER,
            "绳索", "肘部固定，完全伸直手臂", 60));
        db.add(new Exercise("近握卧推", MuscleGroup.ARMS, Difficulty.INTERMEDIATE,
            "杠铃", "握距肩宽内，强化三头肌", 90));
        db.add(new Exercise("仰卧臂屈伸", MuscleGroup.ARMS, Difficulty.INTERMEDIATE,
            "哑铃/EZ杠", "肘部向内，完全伸直后控制", 75));

        // LEGS
        db.add(new Exercise("深蹲", MuscleGroup.LEGS, Difficulty.BEGINNER,
            "徒手/杠铃", "膝盖追脚尖，髋低于膝，挺胸抬头", 120));
        db.add(new Exercise("保加利亚分腿蹲", MuscleGroup.LEGS, Difficulty.INTERMEDIATE,
            "哑铃+凳子", "后脚脚背搭凳，前腿膝不超脚尖", 90));
        db.add(new Exercise("腿举", MuscleGroup.LEGS, Difficulty.BEGINNER,
            "腿举机", "脚踩位置决定目标肌群", 90));
        db.add(new Exercise("腿弯举", MuscleGroup.LEGS, Difficulty.BEGINNER,
            "腿弯举机", "臀部不离垫，峰值收缩停留1秒", 75));
        db.add(new Exercise("罗马尼亚硬拉", MuscleGroup.LEGS, Difficulty.INTERMEDIATE,
            "哑铃", "强化腘绳肌，软腿站姿", 90));
        db.add(new Exercise("臀桥", MuscleGroup.GLUTES, Difficulty.BEGINNER,
            "徒手/杠铃", "挤压臀肌到顶，脊柱中立", 60));
        db.add(new Exercise("贝壳式", MuscleGroup.GLUTES, Difficulty.BEGINNER,
            "弹力带", "侧卧，膝盖打开，强化臀中肌", 60));

        // CORE
        db.add(new Exercise("卷腹", MuscleGroup.CORE, Difficulty.BEGINNER,
            "徒手", "腰部保持地面，只卷曲上背", 45));
        db.add(new Exercise("平板支撑", MuscleGroup.CORE, Difficulty.BEGINNER,
            "徒手", "肘肩对齐，臀部不抬高不塌陷", 45));
        db.add(new Exercise("俄罗斯转体", MuscleGroup.CORE, Difficulty.INTERMEDIATE,
            "哑铃/药球", "脚离地更难，躯干稳定转动", 60));
        db.add(new Exercise("悬挂抬腿", MuscleGroup.CORE, Difficulty.ADVANCED,
            "单杠", "腰椎主动后倾，膝上抬至腰部", 90));
        db.add(new Exercise("死虫式", MuscleGroup.CORE, Difficulty.BEGINNER,
            "徒手", "腰背全程贴地，缓慢伸展四肢", 45));
        db.add(new Exercise("侧平板支撑", MuscleGroup.CORE, Difficulty.INTERMEDIATE,
            "徒手", "髋部抬离地面，身体成直线", 60));

        // FULL BODY
        db.add(new Exercise("波比跳", MuscleGroup.FULL_BODY, Difficulty.INTERMEDIATE,
            "徒手", "俯卧撑+跳跃组合，全身有氧增强", 60));
        db.add(new Exercise("壶铃摇摆", MuscleGroup.FULL_BODY, Difficulty.INTERMEDIATE,
            "壶铃", "髋铰链驱动，非蹲动作", 90));
        db.add(new Exercise("跳箱", MuscleGroup.FULL_BODY, Difficulty.INTERMEDIATE,
            "跳箱", "落地时膝盖微曲缓冲", 90));
        db.add(new Exercise("慢跑", MuscleGroup.FULL_BODY, Difficulty.BEGINNER,
            "徒手", "Zone 2心率，60-70%最大心率", 0));

        return db;
    }

    // ── Recommendation methods ────────────────────────────────────────────────

    public static List<Exercise> recommend(MuscleGroup group, Difficulty maxLevel, int count) {
        return allExercises().stream()
                .filter(e -> e.group() == group)
                .filter(e -> e.level().ordinal() <= maxLevel.ordinal())
                .limit(count)
                .toList();
    }

    public static Difficulty difficultyFromBmi(double bmi) {
        if (bmi > 30) return Difficulty.BEGINNER;
        if (bmi > 25) return Difficulty.BEGINNER;
        return Difficulty.INTERMEDIATE;
    }

    /**
     * 根据 BMI 和目标体型生成完整的周训练计划建议
     */
    public static String weeklyPlanSummary(double bmi, double targetBmi, String bodyType) {
        Difficulty diff = difficultyFromBmi(bmi);
        boolean needsCut = bmi > targetBmi + 1.0;
        boolean needsBulk = bmi < targetBmi - 1.0;

        StringBuilder sb = new StringBuilder();
        sb.append("推荐训练强度: ").append(diff.name()).append("\n");

        if (needsCut) {
            sb.append("减脂优先模式:\n");
            sb.append("• 力量训练 3次/周 × 45-55分钟\n");
            sb.append("• 有氧 2-3次/周 × 30-40分钟 (Zone2)\n");
            sb.append("• 推荐动作: 深蹲、硬拉、推举 + 跑步/骑车\n");
        } else if (needsBulk) {
            sb.append("增肌优先模式:\n");
            sb.append("• 力量训练 4次/周 × 55-70分钟\n");
            sb.append("• 有氧 1-2次/周（轻度）\n");
            sb.append("• 推荐动作: 大重量复合动作为主\n");
        } else {
            sb.append("维持体型模式:\n");
            sb.append("• 力量训练 3次/周 × 45-60分钟\n");
            sb.append("• 有氧 1-2次/周\n");
            sb.append("• 综合训练，全身均衡发展\n");
        }

        // Add top exercises for each key muscle group
        sb.append("\n推荐核心动作（每组 8-12次 × 3组）:\n");
        List<MuscleGroup> keyGroups = List.of(
                MuscleGroup.LEGS, MuscleGroup.BACK, MuscleGroup.CHEST,
                MuscleGroup.SHOULDERS, MuscleGroup.CORE);
        for (MuscleGroup mg : keyGroups) {
            List<Exercise> exs = recommend(mg, diff, 2);
            if (!exs.isEmpty()) {
                sb.append("• ").append(mg.getLabel()).append(": ");
                sb.append(exs.stream().map(Exercise::name)
                        .reduce((a, b) -> a + "、" + b).orElse(""));
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
