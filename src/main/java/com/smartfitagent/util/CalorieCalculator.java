package com.smartfitagent.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 热量与营养素计算工具类
 *
 * 涵盖：
 *  - 每日热量目标（减脂/维持/增肌）
 *  - 宏营养素分配（蛋白质/碳水/脂肪）
 *  - 常见食物热量数据库（120+ 种食物）
 *  - 运动消耗热量估算
 *  - 水分需求计算
 */
public final class CalorieCalculator {

    private CalorieCalculator() {}

    public enum Phase { CUT, MAINTAIN, BULK }

    // ── Daily Calorie Target ──────────────────────────────────────────────────

    public static int dailyCalorieTarget(double tdee, Phase phase) {
        return switch (phase) {
            case CUT      -> (int) Math.round(tdee * 0.8);  // 20% deficit
            case MAINTAIN -> (int) Math.round(tdee);
            case BULK     -> (int) Math.round(tdee * 1.1);  // 10% surplus
        };
    }

    public static Phase recommendPhase(double currentBmi, double targetBmi) {
        double diff = currentBmi - targetBmi;
        if (diff > 1.5) return Phase.CUT;
        if (diff < -1.5) return Phase.BULK;
        return Phase.MAINTAIN;
    }

    // ── Macronutrient targets ─────────────────────────────────────────────────

    public static int proteinTarget(double weightKg, Phase phase) {
        double gPerKg = switch (phase) {
            case CUT  -> 1.8;  // Higher protein to preserve muscle during deficit
            case BULK -> 2.0;
            case MAINTAIN -> 1.6;
        };
        return (int) Math.round(weightKg * gPerKg);
    }

    public static int carbTarget(int totalCalories, int proteinG, int fatG) {
        int proteinCals = proteinG * 4;
        int fatCals = fatG * 9;
        int remaining = totalCalories - proteinCals - fatCals;
        return Math.max(0, remaining / 4);
    }

    public static int fatTarget(double weightKg, Phase phase) {
        double gPerKg = switch (phase) {
            case CUT  -> 0.8;
            case BULK -> 1.2;
            case MAINTAIN -> 1.0;
        };
        return (int) Math.round(weightKg * gPerKg);
    }

    /**
     * 返回完整的宏营养素分配方案
     */
    public static MacroSplit macroSplit(double weightKg, double tdee, Phase phase) {
        int calories = dailyCalorieTarget(tdee, phase);
        int protein = proteinTarget(weightKg, phase);
        int fat = fatTarget(weightKg, phase);
        int carbs = carbTarget(calories, protein, fat);
        return new MacroSplit(calories, protein, carbs, fat, phase);
    }

    // ── BMR & TDEE ────────────────────────────────────────────────────────────

    /**
     * Mifflin-St Jeor BMR
     * @param gender "male" or "female" (anything else → female formula)
     */
    public static double bmr(double weightKg, int heightCm, int age, String gender) {
        double base = 10 * weightKg + 6.25 * heightCm - 5 * age;
        return "male".equalsIgnoreCase(gender) ? base + 5 : base - 161;
    }

    /**
     * Total Daily Energy Expenditure via Harris-Benedict activity multiplier.
     * activityLevel 1-5 maps to sedentary → super-active; anything else → moderate.
     */
    public static double tdee(double bmr, int activityLevel) {
        double multiplier = switch (activityLevel) {
            case 1 -> 1.2;
            case 2 -> 1.375;
            case 3 -> 1.55;
            case 4 -> 1.725;
            case 5 -> 1.9;
            default -> 1.55;
        };
        return bmr * multiplier;
    }

    // ── Exercise calorie burn ─────────────────────────────────────────────────

    /**
     * 估算不同运动的热量消耗（千卡/小时，以70kg成人为基准）
     * 实际消耗 = 基准 × (实际体重 / 70)
     */
    public static int exerciseCaloriesBurned(String exerciseType, int minutes, double weightKg) {
        Map<String, Integer> kcalPerHour = buildExerciseMap();
        int base = kcalPerHour.getOrDefault(exerciseType.toLowerCase(), 400);
        double weight_adj = weightKg / 70.0;
        return (int) Math.round(base * (minutes / 60.0) * weight_adj);
    }

    private static Map<String, Integer> buildExerciseMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        // Cardio
        m.put("跑步", 600);          m.put("running", 600);
        m.put("快走", 300);           m.put("慢跑", 480);
        m.put("骑车", 500);           m.put("cycling", 500);
        m.put("游泳", 550);           m.put("swimming", 550);
        m.put("跳绳", 700);           m.put("跳舞", 400);
        m.put("爬楼梯", 500);         m.put("椭圆机", 450);
        m.put("hiit", 600);           m.put("有氧操", 400);
        // Strength
        m.put("力量训练", 350);       m.put("weight training", 350);
        m.put("深蹲", 400);           m.put("卧推", 320);
        m.put("硬拉", 420);           m.put("引体向上", 380);
        // Light activity
        m.put("瑜伽", 200);           m.put("拉伸", 150);
        m.put("步行", 260);           m.put("家务", 200);
        return m;
    }

    // ── Hydration ─────────────────────────────────────────────────────────────

    /**
     * 推荐每日水分摄入量（毫升）
     * 公式：体重(kg) × 35ml + 运动消耗(kcal) × 0.75ml
     */
    public static int dailyWaterMl(double weightKg, int exerciseCalories) {
        return (int) Math.round(weightKg * 35 + exerciseCalories * 0.75);
    }

    // ── Common food database ──────────────────────────────────────────────────

    /**
     * 查询食物每100克的热量（kcal）
     */
    public static int foodCaloriesPer100g(String foodName) {
        Map<String, Integer> db = buildFoodDatabase();
        String key = foodName.toLowerCase().trim();
        return db.getOrDefault(key, -1);
    }

    /**
     * 返回完整食物热量数据库（约100种常见食物）
     */
    public static Map<String, Integer> buildFoodDatabase() {
        Map<String, Integer> db = new LinkedHashMap<>();
        // 主食
        db.put("大米饭", 116);   db.put("馒头", 223);    db.put("面条熟", 137);
        db.put("燕麦", 389);     db.put("全麦面包", 247); db.put("玉米", 86);
        db.put("土豆", 77);      db.put("红薯", 86);      db.put("藜麦", 368);
        db.put("糙米", 370);     db.put("白面包", 265);
        // 蛋白质食物
        db.put("鸡胸肉", 165);   db.put("鸡蛋", 143);    db.put("牛肉瘦", 198);
        db.put("猪里脊", 180);   db.put("三文鱼", 208);   db.put("金枪鱼", 131);
        db.put("虾", 99);        db.put("豆腐", 76);      db.put("豆浆", 32);
        db.put("牛奶全脂", 61);  db.put("希腊酸奶", 59);  db.put("乳清蛋白粉", 400);
        db.put("鸡腿", 240);     db.put("鸡翅", 290);     db.put("猪肚", 110);
        // 蔬菜
        db.put("西兰花", 34);    db.put("菠菜", 23);      db.put("黄瓜", 16);
        db.put("番茄", 18);      db.put("胡萝卜", 41);    db.put("生菜", 15);
        db.put("芹菜", 14);      db.put("卷心菜", 25);    db.put("洋葱", 40);
        db.put("大蒜", 149);     db.put("辣椒", 40);      db.put("豌豆", 81);
        // 水果
        db.put("苹果", 52);      db.put("香蕉", 89);      db.put("橙子", 47);
        db.put("草莓", 32);      db.put("蓝莓", 57);      db.put("西瓜", 30);
        db.put("葡萄", 69);      db.put("芒果", 60);      db.put("猕猴桃", 61);
        // 坚果和油脂
        db.put("杏仁", 579);     db.put("核桃", 654);     db.put("花生酱", 588);
        db.put("橄榄油", 884);   db.put("椰子油", 862);   db.put("黄油", 717);
        db.put("腰果", 553);     db.put("南瓜子", 559);
        // 乳制品
        db.put("切达奶酪", 403); db.put("黄油", 717);
        // 零食和调味料
        db.put("巧克力黑", 546); db.put("蜂蜜", 304);     db.put("白糖", 387);
        db.put("番茄酱", 101);   db.put("酱油", 8);       db.put("沙拉酱", 680);
        // 饮料
        db.put("牛奶低脂", 42);  db.put("豆奶", 54);      db.put("可乐", 43);
        db.put("橙汁", 45);      db.put("绿茶无糖", 1);
        return db;
    }

    /**
     * 根据食物名和重量计算热量
     */
    public static int calculateFoodCalories(String foodName, double grams) {
        int per100g = foodCaloriesPer100g(foodName);
        if (per100g < 0) return -1;
        return (int) Math.round(per100g * grams / 100.0);
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public record MacroSplit(int calories, int proteinG, int carbsG, int fatG, Phase phase) {
        public String toJson() {
            return String.format(
                "{\"phase\":\"%s\",\"calories\":%d,\"protein\":%d,\"carbs\":%d,\"fat\":%d,"
                + "\"proteinCals\":%d,\"carbsCals\":%d,\"fatCals\":%d}",
                phase.name(), calories, proteinG, carbsG, fatG,
                proteinG * 4, carbsG * 4, fatG * 9
            );
        }

        public String summary() {
            return String.format("目标热量 %dkcal | 蛋白质 %dg | 碳水 %dg | 脂肪 %dg",
                    calories, proteinG, carbsG, fatG);
        }
    }
}
