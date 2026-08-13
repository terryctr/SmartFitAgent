package com.smartfitagent.util;

/**
 * BMI 计算工具类
 *
 * 提供精确的 BMI 计算、分类和目标体重推荐，
 * 支持中国标准（18.5-23.9 正常范围）和 WHO 标准（18.5-24.9）。
 */
public final class BmiCalculator {

    public enum Standard { CHINA, WHO }

    private BmiCalculator() {}

    public static double calculate(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) return 0.0;
        double meters = heightCm / 100.0;
        return Math.round((weightKg / (meters * meters)) * 10.0) / 10.0;
    }

    public static String classify(double bmi, Standard standard) {
        if (bmi <= 0) return "数据不足";
        if (standard == Standard.CHINA) {
            if (bmi < 18.5) return "体重偏轻";
            if (bmi < 24.0) return "正常范围";
            if (bmi < 28.0) return "超重";
            return "肥胖";
        } else {
            if (bmi < 18.5) return "偏轻 (Underweight)";
            if (bmi < 25.0) return "正常 (Normal)";
            if (bmi < 30.0) return "超重 (Overweight)";
            return "肥胖 (Obese)";
        }
    }

    public static String classify(double bmi) {
        return classify(bmi, Standard.CHINA);
    }

    public static double targetWeight(double heightCm, double targetBmi) {
        if (heightCm <= 0) return 0.0;
        double meters = heightCm / 100.0;
        return Math.round(targetBmi * meters * meters * 10.0) / 10.0;
    }

    public static double targetBmi(String bodyType) {
        if (bodyType == null) return 22.0;
        return switch (bodyType.trim()) {
            case "偏瘦", "纤细", "lean"     -> 20.0;
            case "强壮", "muscular", "肌肉型" -> 24.0;
            case "正常", "匀称", "normal"    -> 22.0;
            default -> 22.0;
        };
    }

    public static double normalWeightMin(double heightCm) {
        return targetWeight(heightCm, 18.5);
    }

    public static double normalWeightMax(double heightCm, Standard standard) {
        double maxBmi = (standard == Standard.CHINA) ? 23.9 : 24.9;
        return targetWeight(heightCm, maxBmi);
    }

    public static double weightToLose(double current, double target) {
        return Math.round((current - target) * 10.0) / 10.0;
    }

    /**
     * 估算达到目标体重所需周数（以每周 0.5kg 健康减脂速度）
     */
    public static int estimateWeeksToGoal(double currentWeight, double targetWeight) {
        double diff = Math.abs(currentWeight - targetWeight);
        return (int) Math.ceil(diff / 0.5);
    }

    /**
     * 估算基础代谢率（BMR）— Mifflin-St Jeor 公式
     * @param gender "male" 或 "female"
     */
    public static double bmr(double weightKg, double heightCm, int ageYears, String gender) {
        double base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears;
        return "male".equalsIgnoreCase(gender) ? base + 5 : base - 161;
    }

    /**
     * 估算每日总热量消耗（TDEE）— 基于活动水平
     * @param activityLevel 1=久坐, 2=轻微, 3=中等, 4=积极, 5=非常积极
     */
    public static double tdee(double bmr, int activityLevel) {
        double[] multipliers = {1.2, 1.375, 1.55, 1.725, 1.9};
        int idx = Math.min(Math.max(activityLevel - 1, 0), multipliers.length - 1);
        return Math.round(bmr * multipliers[idx]);
    }

    /**
     * 生成 BMI 健康报告摘要
     */
    public static String report(double heightCm, double weightKg, String bodyType) {
        double bmi = calculate(heightCm, weightKg);
        double target = targetBmi(bodyType);
        double targetW = targetWeight(heightCm, target);
        String classification = classify(bmi);
        double delta = Math.round((weightKg - targetW) * 10.0) / 10.0;

        StringBuilder sb = new StringBuilder();
        sb.append("身高: ").append(String.format("%.0f", heightCm)).append("cm | ");
        sb.append("体重: ").append(String.format("%.1f", weightKg)).append("kg | ");
        sb.append("当前BMI: ").append(String.format("%.1f", bmi)).append("（").append(classification).append("）| ");
        sb.append("目标BMI: ").append(String.format("%.1f", target)).append("（").append(bodyType).append("）| ");
        sb.append("目标体重: ").append(String.format("%.1f", targetW)).append("kg");
        if (Math.abs(delta) > 0.5) {
            sb.append(" | ").append(delta > 0 ? "需减重" : "可增重").append(": ")
              .append(String.format("%.1f", Math.abs(delta))).append("kg");
        } else {
            sb.append(" | 已达目标体重范围");
        }
        return sb.toString();
    }

    /**
     * 判断 BMI 是否在目标范围内（±1.0 容差）
     */
    public static boolean isAtTarget(double currentBmi, double targetBmi) {
        return Math.abs(currentBmi - targetBmi) <= 1.0;
    }

    /**
     * 返回 BMI 趋势描述
     */
    public static String trend(double previousBmi, double currentBmi) {
        double diff = currentBmi - previousBmi;
        if (Math.abs(diff) < 0.1) return "BMI 保持稳定";
        if (diff < 0) return String.format("BMI 下降 %.1f（减脂进行中）", Math.abs(diff));
        return String.format("BMI 上升 %.1f（可能增重或增肌）", diff);
    }
}
