package com.smartfitagent.util;

import java.util.*;

/**
 * 训练计划程序生成器
 *
 * 根据用户目标、训练水平、可用天数和设备条件，
 * 动态生成个性化的多周训练计划，
 * 供 AI Agent 和计划管理模块使用。
 */
public class TrainingProgramGenerator {

    // ──────────────────────────────────────────────────────────────
    // Enums
    // ──────────────────────────────────────────────────────────────

    public enum FitnessLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    public enum TrainingGoal {
        FAT_LOSS, MUSCLE_GAIN, STRENGTH, ENDURANCE, GENERAL_FITNESS, ATHLETIC_PERFORMANCE
    }

    public enum Equipment {
        BARBELL, DUMBBELLS, CABLES, MACHINES, BODYWEIGHT_ONLY, FULL_GYM
    }

    // ──────────────────────────────────────────────────────────────
    // Program templates by goal
    // ──────────────────────────────────────────────────────────────

    private static final Map<TrainingGoal, String> GOAL_DESCRIPTIONS = Map.of(
        TrainingGoal.FAT_LOSS,               "减脂塑形",
        TrainingGoal.MUSCLE_GAIN,            "增肌塑造",
        TrainingGoal.STRENGTH,               "力量提升",
        TrainingGoal.ENDURANCE,              "耐力训练",
        TrainingGoal.GENERAL_FITNESS,        "综合健身",
        TrainingGoal.ATHLETIC_PERFORMANCE,   "运动表现"
    );

    /** Sets × Reps prescription by goal + level */
    private static final Map<String, int[]> VOLUME_TABLE = new LinkedHashMap<>();
    static {
        VOLUME_TABLE.put("FAT_LOSS_BEGINNER",       new int[]{3, 15, 60});
        VOLUME_TABLE.put("FAT_LOSS_INTERMEDIATE",   new int[]{4, 12, 75});
        VOLUME_TABLE.put("FAT_LOSS_ADVANCED",       new int[]{5, 12, 90});
        VOLUME_TABLE.put("MUSCLE_GAIN_BEGINNER",    new int[]{3, 10, 90});
        VOLUME_TABLE.put("MUSCLE_GAIN_INTERMEDIATE",new int[]{4, 8,  120});
        VOLUME_TABLE.put("MUSCLE_GAIN_ADVANCED",    new int[]{5, 6,  150});
        VOLUME_TABLE.put("STRENGTH_BEGINNER",       new int[]{3, 5,  180});
        VOLUME_TABLE.put("STRENGTH_INTERMEDIATE",   new int[]{4, 3,  240});
        VOLUME_TABLE.put("STRENGTH_ADVANCED",       new int[]{5, 3,  300});
        VOLUME_TABLE.put("ENDURANCE_BEGINNER",      new int[]{2, 20, 45});
        VOLUME_TABLE.put("ENDURANCE_INTERMEDIATE",  new int[]{3, 20, 60});
        VOLUME_TABLE.put("ENDURANCE_ADVANCED",      new int[]{4, 20, 75});
        VOLUME_TABLE.put("GENERAL_FITNESS_BEGINNER",     new int[]{3, 12, 60});
        VOLUME_TABLE.put("GENERAL_FITNESS_INTERMEDIATE", new int[]{3, 12, 75});
        VOLUME_TABLE.put("GENERAL_FITNESS_ADVANCED",     new int[]{4, 10, 90});
    }

    // ──────────────────────────────────────────────────────────────
    // Exercise database
    // ──────────────────────────────────────────────────────────────

    record Exercise(String name, String muscleGroup, String category, String difficulty, boolean needsEquipment) {}

    private static final List<Exercise> EXERCISE_DB = List.of(
        // Chest
        new Exercise("杠铃卧推",       "胸部", "力量", "中级", true),
        new Exercise("哑铃飞鸟",       "胸部", "力量", "初级", true),
        new Exercise("上斜哑铃卧推",   "胸部", "力量", "中级", true),
        new Exercise("俯卧撑",         "胸部", "力量", "初级", false),
        new Exercise("宽距俯卧撑",     "胸部", "力量", "初级", false),
        // Back
        new Exercise("引体向上",       "背部", "力量", "中级", false),
        new Exercise("坐姿划船",       "背部", "力量", "初级", true),
        new Exercise("硬拉",           "背部", "力量", "高级", true),
        new Exercise("单臂哑铃划船",   "背部", "力量", "初级", true),
        new Exercise("宽握高位下拉",   "背部", "力量", "初级", true),
        new Exercise("俯身哑铃划船",   "背部", "力量", "中级", true),
        // Shoulders
        new Exercise("站姿杠铃推举",   "肩部", "力量", "中级", true),
        new Exercise("哑铃侧平举",     "肩部", "力量", "初级", true),
        new Exercise("前平举",         "肩部", "力量", "初级", true),
        new Exercise("面拉",           "肩部", "力量", "初级", true),
        // Arms
        new Exercise("杠铃弯举",       "手臂", "力量", "初级", true),
        new Exercise("绳索下压",       "手臂", "力量", "初级", true),
        new Exercise("锤式弯举",       "手臂", "力量", "初级", true),
        new Exercise("窄距卧推",       "手臂", "力量", "中级", true),
        new Exercise("双杠臂屈伸",     "手臂", "力量", "中级", false),
        // Legs
        new Exercise("深蹲",           "腿部", "力量", "中级", true),
        new Exercise("腿举",           "腿部", "力量", "初级", true),
        new Exercise("罗马尼亚硬拉",   "腿部", "力量", "中级", true),
        new Exercise("弓步蹲",         "腿部", "力量", "初级", false),
        new Exercise("腿弯举",         "腿部", "力量", "初级", true),
        new Exercise("坐姿腿屈伸",     "腿部", "力量", "初级", true),
        new Exercise("保加利亚式分腿蹲","腿部","力量","中级", false),
        // Core
        new Exercise("平板支撑",       "核心", "核心", "初级", false),
        new Exercise("仰卧起坐",       "核心", "核心", "初级", false),
        new Exercise("卷腹",           "核心", "核心", "初级", false),
        new Exercise("俄罗斯转体",     "核心", "核心", "初级", false),
        new Exercise("悬垂举腿",       "核心", "核心", "中级", false),
        new Exercise("V字起坐",        "核心", "核心", "中级", false),
        // Cardio
        new Exercise("跑步机",         "全身", "有氧", "初级", true),
        new Exercise("椭圆机",         "全身", "有氧", "初级", true),
        new Exercise("跳绳",           "全身", "有氧", "初级", false),
        new Exercise("波比跳",         "全身", "有氧", "中级", false),
        new Exercise("开合跳",         "全身", "有氧", "初级", false),
        new Exercise("山地爬行",       "全身", "有氧", "中级", false)
    );

    // ──────────────────────────────────────────────────────────────
    // Program generation
    // ──────────────────────────────────────────────────────────────

    /**
     * 生成完整的多周训练计划
     *
     * @param goal         训练目标
     * @param level        训练水平
     * @param daysPerWeek  每周训练天数（3-6）
     * @param equipment    可用设备
     * @param weeks        计划周数（4-12）
     * @return 结构化训练计划
     */
    public static Map<String, Object> generate(
            TrainingGoal goal,
            FitnessLevel level,
            int daysPerWeek,
            Equipment equipment,
            int weeks) {

        daysPerWeek = Math.max(3, Math.min(6, daysPerWeek));
        weeks = Math.max(4, Math.min(12, weeks));

        Map<String, Object> program = new LinkedHashMap<>();
        program.put("goal", GOAL_DESCRIPTIONS.get(goal));
        program.put("level", levelLabel(level));
        program.put("daysPerWeek", daysPerWeek);
        program.put("weeks", weeks);
        program.put("equipment", equipment.name());

        // Volume prescription
        String key = goal.name() + "_" + level.name();
        int[] vol = VOLUME_TABLE.getOrDefault(key, new int[]{3, 10, 90});
        program.put("setsPerExercise", vol[0]);
        program.put("repsPerSet", vol[1]);
        program.put("restSeconds", vol[2]);

        // Split type
        String split = chooseSplit(daysPerWeek, goal);
        program.put("splitType", split);

        // Weekly schedule
        List<Map<String, Object>> schedule = buildWeeklySchedule(split, daysPerWeek, goal, level, equipment);
        program.put("weeklySchedule", schedule);

        // Progression rules
        program.put("progressionRules", buildProgressionRules(goal, level));

        // Phase overview
        program.put("phases", buildPhases(weeks, goal));

        // Nutrition guidance
        program.put("nutritionGuidance", buildNutritionGuidance(goal));

        // Deload recommendation
        program.put("deloadSchedule", "每4周进行1次减量周（减少30-40%训练量）");

        return program;
    }

    // ──────────────────────────────────────────────────────────────
    // Split selection
    // ──────────────────────────────────────────────────────────────

    private static String chooseSplit(int days, TrainingGoal goal) {
        return switch (days) {
            case 3 -> goal == TrainingGoal.STRENGTH ? "全身力量三分化" : "推拉腿三分化";
            case 4 -> "上下肢四分化";
            case 5 -> "胸/背/肩/腿/手臂五分化";
            case 6 -> "推拉腿 × 2 六分化";
            default -> "全身训练";
        };
    }

    private static List<Map<String, Object>> buildWeeklySchedule(
            String split,
            int days,
            TrainingGoal goal,
            FitnessLevel level,
            Equipment equipment) {

        List<Map<String, Object>> schedule = new ArrayList<>();
        List<String> dayNames = List.of("周一","周二","周三","周四","周五","周六","周日");
        List<String> focus = getFocusByDays(days, split);

        for (int i = 0; i < 7; i++) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("day", dayNames.get(i));
            if (i < days) {
                String f = focus.get(i % focus.size());
                day.put("focus", f);
                day.put("exercises", selectExercises(f, goal, level, equipment));
                day.put("estimatedMinutes", 45 + level.ordinal() * 15);
            } else {
                day.put("focus", "休息/主动恢复");
                day.put("exercises", List.of());
                day.put("estimatedMinutes", 20);
            }
            schedule.add(day);
        }
        return schedule;
    }

    private static List<String> getFocusByDays(int days, String split) {
        if (days == 3) return List.of("推(胸/肩/三头)", "拉(背/二头)", "腿/核心");
        if (days == 4) return List.of("上肢推", "下肢", "上肢拉", "下肢/核心");
        if (days == 5) return List.of("胸", "背", "肩", "腿", "手臂/核心");
        return List.of("胸/三头", "背/二头", "腿", "胸/三头", "背/二头", "腿/肩");
    }

    private static List<Map<String, Object>> selectExercises(
            String focus, TrainingGoal goal, FitnessLevel level, Equipment equipment) {

        boolean needsEquip = equipment != Equipment.BODYWEIGHT_ONLY;
        boolean beginner = level == FitnessLevel.BEGINNER;

        List<Exercise> candidates = EXERCISE_DB.stream()
            .filter(e -> matchesFocus(e, focus))
            .filter(e -> !e.needsEquipment() || needsEquip)
            .filter(e -> !beginner || !e.difficulty().equals("高级"))
            .limit(goal == TrainingGoal.FAT_LOSS ? 6 : 5)
            .toList();

        return candidates.stream().map(e -> Map.<String, Object>of(
            "name", e.name(),
            "muscle", e.muscleGroup(),
            "category", e.category()
        )).toList();
    }

    private static boolean matchesFocus(Exercise e, String focus) {
        if (focus.contains("胸")) return e.muscleGroup().contains("胸") || focus.contains(e.muscleGroup());
        if (focus.contains("背")) return e.muscleGroup().contains("背");
        if (focus.contains("腿")) return e.muscleGroup().contains("腿");
        if (focus.contains("肩")) return e.muscleGroup().contains("肩");
        if (focus.contains("手臂")) return e.muscleGroup().contains("手臂");
        if (focus.contains("核心")) return e.muscleGroup().contains("核心");
        if (focus.contains("推")) return Set.of("胸部","肩部","手臂").contains(e.muscleGroup());
        if (focus.contains("拉")) return Set.of("背部","手臂").contains(e.muscleGroup());
        if (focus.contains("上肢")) return Set.of("胸部","背部","肩部","手臂").contains(e.muscleGroup());
        if (focus.contains("下肢")) return Set.of("腿部","核心").contains(e.muscleGroup());
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    // Supporting plan structures
    // ──────────────────────────────────────────────────────────────

    private static List<String> buildProgressionRules(TrainingGoal goal, FitnessLevel level) {
        List<String> rules = new ArrayList<>();
        if (goal == TrainingGoal.STRENGTH) {
            rules.add("每次训练后，主项若完成所有组，下次增重 2.5kg（上肢）或 5kg（下肢）");
            rules.add("采用线性进阶：先增加重量，再增加组数");
        } else if (goal == TrainingGoal.MUSCLE_GAIN) {
            rules.add("双重进阶：先增加次数到上限，再增加重量 5-10%");
            rules.add("每4周评估一次，调整基础重量");
        } else if (goal == TrainingGoal.FAT_LOSS) {
            rules.add("优先保持训练重量，减少组间休息时间以提高心率");
            rules.add("每2周增加1组训练量，保持高代谢刺激");
        } else {
            rules.add("每2周尝试增加一次重量或次数");
            rules.add("注重动作质量胜于重量");
        }
        if (level == FitnessLevel.BEGINNER) {
            rules.add("新手阶段每次训练都应有进步，坚持3个月打好基础");
        }
        rules.add("如连续2次无法完成计划组次，降低重量 5-10%");
        return rules;
    }

    private static List<Map<String, Object>> buildPhases(int totalWeeks, TrainingGoal goal) {
        List<Map<String, Object>> phases = new ArrayList<>();
        int w = 1;

        // Phase 1: Foundation (Weeks 1-4)
        int phase1End = Math.min(4, totalWeeks);
        phases.add(Map.of(
            "phase", "第一阶段：基础建立",
            "weeks", w + "-" + phase1End,
            "focus", "动作模式学习、建立训练习惯",
            "intensity", "低-中等"
        ));
        w = phase1End + 1;

        if (totalWeeks > 4) {
            // Phase 2: Development (Weeks 5-8)
            int phase2End = Math.min(8, totalWeeks);
            phases.add(Map.of(
                "phase", "第二阶段：强化发展",
                "weeks", w + "-" + phase2End,
                "focus", goal == TrainingGoal.FAT_LOSS ? "提高代谢强度" : "增加训练量",
                "intensity", "中-高等"
            ));
            w = phase2End + 1;
        }

        if (totalWeeks > 8) {
            // Phase 3: Peak (Weeks 9+)
            phases.add(Map.of(
                "phase", "第三阶段：冲刺巅峰",
                "weeks", w + "-" + totalWeeks,
                "focus", "最大化效果，接近目标",
                "intensity", "高"
            ));
        }

        return phases;
    }

    private static Map<String, Object> buildNutritionGuidance(TrainingGoal goal) {
        Map<String, Object> n = new LinkedHashMap<>();
        return switch (goal) {
            case FAT_LOSS -> {
                n.put("calorieStrategy", "热量缺口 300-500 kcal/天");
                n.put("proteinTarget", "2.0-2.4 g/kg 体重");
                n.put("carbStrategy", "训练日保证充足碳水，休息日减少");
                n.put("timing", "训练前1-2小时进食，训练后30分钟内摄入蛋白质");
                yield n;
            }
            case MUSCLE_GAIN -> {
                n.put("calorieStrategy", "热量盈余 200-300 kcal/天");
                n.put("proteinTarget", "1.8-2.2 g/kg 体重");
                n.put("carbStrategy", "全天保持充足碳水，以支持训练表现");
                n.put("timing", "训练前后各摄入蛋白质+碳水混合餐");
                yield n;
            }
            case STRENGTH -> {
                n.put("calorieStrategy", "轻微热量盈余 100-200 kcal/天");
                n.put("proteinTarget", "1.6-2.0 g/kg 体重");
                n.put("carbStrategy", "重点保证大重量训练前的碳水储备");
                n.put("timing", "训练前2-3小时正餐，训练后补充蛋白质");
                yield n;
            }
            default -> {
                n.put("calorieStrategy", "维持热量平衡");
                n.put("proteinTarget", "1.4-1.8 g/kg 体重");
                n.put("carbStrategy", "均衡摄入，注重食物多样性");
                n.put("timing", "规律三餐，训练后适量补充蛋白质");
                yield n;
            }
        };
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private static String levelLabel(FitnessLevel level) {
        return switch (level) {
            case BEGINNER     -> "初学者";
            case INTERMEDIATE -> "中级";
            case ADVANCED     -> "高级";
        };
    }

    /**
     * 生成文字版周计划（供 AI Prompt 使用）
     */
    public static String toTextSummary(Map<String, Object> program) {
        StringBuilder sb = new StringBuilder();
        sb.append("训练目标: ").append(program.get("goal"))
          .append(" | 水平: ").append(program.get("level"))
          .append(" | ").append(program.get("daysPerWeek")).append("天/周\n");
        sb.append("分化方式: ").append(program.get("splitType")).append("\n");
        sb.append("每组").append(program.get("setsPerExercise")).append("组 × ")
          .append(program.get("repsPerSet")).append("次，休息")
          .append(program.get("restSeconds")).append("秒\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) program.get("weeklySchedule");
        if (schedule != null) {
            schedule.stream()
                .filter(d -> !d.get("focus").toString().contains("休息"))
                .forEach(d -> {
                    sb.append(d.get("day")).append(": ").append(d.get("focus"));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> exs = (List<Map<String, Object>>) d.get("exercises");
                    if (exs != null && !exs.isEmpty()) {
                        sb.append(" (").append(exs.stream().map(e -> e.get("name").toString()).reduce("", (a,b) -> a.isEmpty() ? b : a + "、" + b)).append(")");
                    }
                    sb.append("\n");
                });
        }
        return sb.toString();
    }
}
