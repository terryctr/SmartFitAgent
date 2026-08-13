package com.smartfitagent.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 训练进度报告生成器
 *
 * 分析多维度训练数据，生成结构化的进度报告，
 * 供 AI Agent 和前端数据分析页面使用。
 */
public class ProgressReporter {

    // ──────────────────────────────────────────────────────────────
    // Public report entry points
    // ──────────────────────────────────────────────────────────────

    /**
     * 生成完整的月度进度报告
     */
    public static Map<String, Object> monthlyReport(
            List<Map<String, Object>> workouts,
            List<Map<String, Object>> nutrition,
            List<Map<String, Object>> measurements,
            String goal) {

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", LocalDate.now().toString());
        report.put("goal", goal);

        // --- 训练维度 ---
        report.put("workoutSummary",    buildWorkoutSummary(workouts));
        report.put("streakAnalysis",    buildStreakAnalysis(workouts));
        report.put("volumeProgression", buildVolumeProgression(workouts));

        // --- 营养维度 ---
        report.put("nutritionSummary",  buildNutritionSummary(nutrition));
        report.put("macroCompliance",   buildMacroCompliance(nutrition));

        // --- 身体成分维度 ---
        report.put("bodyComposition",   buildBodyComposition(measurements));

        // --- 综合评分 ---
        Map<String, Object> score = buildCompositeScore(report);
        report.put("compositeScore", score);
        report.put("progressGrade", gradeFromScore((Integer) score.get("total")));

        // --- AI 提示摘要（供 Pipeline 使用） ---
        report.put("aiSummary", buildAiSummary(report));

        return report;
    }

    /**
     * 生成简版进度摘要（供 Prompt 注入使用）
     */
    public static String briefSummary(
            List<Map<String, Object>> workouts,
            List<Map<String, Object>> measurements) {

        StringBuilder sb = new StringBuilder();

        if (!workouts.isEmpty()) {
            int count = workouts.size();
            OptionalDouble avgDur = workouts.stream()
                .mapToDouble(w -> toDouble(w.get("duration_minutes")))
                .average();
            sb.append("近期训练: ").append(count).append("次");
            avgDur.ifPresent(d -> sb.append("，平均").append(Math.round(d)).append("分钟/次"));
            sb.append("。");
        } else {
            sb.append("暂无训练记录。");
        }

        if (!measurements.isEmpty()) {
            Map<String, Object> latest = measurements.get(0);
            Object w = latest.get("weight_kg");
            Object f = latest.get("body_fat_pct");
            if (w != null) sb.append(" 最新体重: ").append(w).append("kg。");
            if (f != null) sb.append(" 体脂率: ").append(f).append("%。");
        }

        return sb.toString().trim();
    }

    // ──────────────────────────────────────────────────────────────
    // Workout analysis
    // ──────────────────────────────────────────────────────────────

    private static Map<String, Object> buildWorkoutSummary(List<Map<String, Object>> workouts) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (workouts.isEmpty()) {
            s.put("sessions", 0);
            s.put("message", "本月暂无训练记录");
            return s;
        }
        s.put("sessions", workouts.size());
        double totalMin = workouts.stream().mapToDouble(w -> toDouble(w.get("duration_minutes"))).sum();
        double totalCal = workouts.stream().mapToDouble(w -> toDouble(w.get("calories_burned"))).sum();
        s.put("totalMinutes", Math.round(totalMin));
        s.put("totalCaloriesBurned", Math.round(totalCal));
        s.put("avgDurationMinutes", Math.round(totalMin / workouts.size()));

        // Most common workout type
        workouts.stream()
            .map(w -> strVal(w.get("workout_type"), ""))
            .filter(t -> !t.isEmpty())
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .ifPresent(e -> s.put("dominantType", e.getKey()));

        return s;
    }

    private static Map<String, Object> buildStreakAnalysis(List<Map<String, Object>> workouts) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (workouts.isEmpty()) {
            s.put("currentStreak", 0);
            s.put("longestStreak", 0);
            return s;
        }

        // Sort by date descending, extract unique dates
        List<LocalDate> dates = workouts.stream()
            .map(w -> parseDate(strVal(w.get("workout_date"), "")))
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();

        if (dates.isEmpty()) {
            s.put("currentStreak", 0);
            s.put("longestStreak", 0);
            return s;
        }

        // Current streak (from today going backward)
        int current = 0;
        LocalDate cursor = LocalDate.now();
        for (LocalDate d : dates) {
            if (d.isEqual(cursor) || d.isEqual(cursor.minusDays(1))) {
                current++;
                cursor = d;
            } else {
                break;
            }
        }

        // Longest streak
        int longest = 0, streak = 1;
        List<LocalDate> sorted = new ArrayList<>(dates);
        Collections.sort(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            if (ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i)) == 1) {
                streak++;
            } else {
                longest = Math.max(longest, streak);
                streak = 1;
            }
        }
        longest = Math.max(longest, streak);

        s.put("currentStreak", current);
        s.put("longestStreak", longest);
        s.put("uniqueTrainingDays", dates.size());
        s.put("trainingFrequencyPerWeek", Math.round(dates.size() / 4.0 * 10.0) / 10.0);

        return s;
    }

    private static Map<String, Object> buildVolumeProgression(List<Map<String, Object>> workouts) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (workouts.size() < 2) {
            s.put("trend", "数据不足");
            return s;
        }

        // Split into first half and second half
        int half = workouts.size() / 2;
        List<Map<String, Object>> newer = workouts.subList(0, half);
        List<Map<String, Object>> older = workouts.subList(half, workouts.size());

        double avgNew = newer.stream().mapToDouble(w -> toDouble(w.get("duration_minutes"))).average().orElse(0);
        double avgOld = older.stream().mapToDouble(w -> toDouble(w.get("duration_minutes"))).average().orElse(0);

        double pctChange = avgOld > 0 ? (avgNew - avgOld) / avgOld * 100 : 0;
        s.put("recentAvgMinutes", Math.round(avgNew));
        s.put("previousAvgMinutes", Math.round(avgOld));
        s.put("volumeChangePct", Math.round(pctChange));
        s.put("trend", pctChange > 5 ? "上升" : pctChange < -5 ? "下降" : "稳定");

        return s;
    }

    // ──────────────────────────────────────────────────────────────
    // Nutrition analysis
    // ──────────────────────────────────────────────────────────────

    private static Map<String, Object> buildNutritionSummary(List<Map<String, Object>> nutrition) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (nutrition.isEmpty()) {
            s.put("loggingDays", 0);
            s.put("message", "本月暂无饮食记录");
            return s;
        }
        s.put("loggingDays", nutrition.size());

        double avgCal  = nutrition.stream().mapToDouble(n -> toDouble(n.get("total_calories"))).average().orElse(0);
        double avgProt = nutrition.stream().mapToDouble(n -> toDouble(n.get("total_protein_g"))).average().orElse(0);
        double avgCarb = nutrition.stream().mapToDouble(n -> toDouble(n.get("total_carbs_g"))).average().orElse(0);
        double avgFat  = nutrition.stream().mapToDouble(n -> toDouble(n.get("total_fat_g"))).average().orElse(0);

        s.put("avgCalories",  Math.round(avgCal));
        s.put("avgProteinG",  Math.round(avgProt));
        s.put("avgCarbsG",    Math.round(avgCarb));
        s.put("avgFatG",      Math.round(avgFat));

        return s;
    }

    private static Map<String, Object> buildMacroCompliance(List<Map<String, Object>> nutrition) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (nutrition.isEmpty()) { s.put("complianceRate", 0); return s; }

        // Days where protein >= 1.5g/kg (assume 70kg default)
        long protDays = nutrition.stream()
            .filter(n -> toDouble(n.get("total_protein_g")) >= 105)
            .count();
        s.put("proteinAdequateDays", protDays);
        s.put("proteinComplianceRate", Math.round(100.0 * protDays / nutrition.size()));

        long calDays = nutrition.stream()
            .filter(n -> { double c = toDouble(n.get("total_calories")); return c >= 1400 && c <= 2800; })
            .count();
        s.put("calorieRangeAdherence", Math.round(100.0 * calDays / nutrition.size()));

        return s;
    }

    // ──────────────────────────────────────────────────────────────
    // Body composition
    // ──────────────────────────────────────────────────────────────

    private static Map<String, Object> buildBodyComposition(List<Map<String, Object>> measurements) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (measurements.isEmpty()) {
            s.put("records", 0);
            s.put("message", "暂无测量记录");
            return s;
        }
        s.put("records", measurements.size());

        Map<String, Object> latest = measurements.get(0);
        s.put("currentWeight",   latest.get("weight_kg"));
        s.put("currentBodyFat",  latest.get("body_fat_pct"));
        s.put("currentWaist",    latest.get("waist_cm"));

        if (measurements.size() > 1) {
            Map<String, Object> earliest = measurements.get(measurements.size() - 1);
            double wDiff = toDouble(latest.get("weight_kg")) - toDouble(earliest.get("weight_kg"));
            double fDiff = toDouble(latest.get("body_fat_pct")) - toDouble(earliest.get("body_fat_pct"));
            s.put("weightChangeKg",   Math.round(wDiff * 10.0) / 10.0);
            s.put("bodyFatChangePct", Math.round(fDiff * 10.0) / 10.0);
            s.put("direction", wDiff < 0 ? "体重下降" : wDiff > 0 ? "体重上升" : "体重稳定");
        }

        return s;
    }

    // ──────────────────────────────────────────────────────────────
    // Composite scoring
    // ──────────────────────────────────────────────────────────────

    private static Map<String, Object> buildCompositeScore(Map<String, Object> report) {
        int score = 0;
        Map<String, Integer> breakdown = new LinkedHashMap<>();

        // Training frequency (max 30)
        Map<?, ?> workout = (Map<?, ?>) report.get("workoutSummary");
        if (workout != null) {
            int sessions = toInt(workout.get("sessions"));
            int ts = Math.min(30, sessions * 2);
            breakdown.put("trainingFrequency", ts);
            score += ts;
        }

        // Streak (max 20)
        Map<?, ?> streak = (Map<?, ?>) report.get("streakAnalysis");
        if (streak != null) {
            int cs = Math.min(20, toInt(streak.get("currentStreak")) * 2);
            breakdown.put("streak", cs);
            score += cs;
        }

        // Nutrition logging (max 25)
        Map<?, ?> nutrition = (Map<?, ?>) report.get("nutritionSummary");
        if (nutrition != null) {
            int days = toInt(nutrition.get("loggingDays"));
            int ns = Math.min(25, days);
            breakdown.put("nutritionLogging", ns);
            score += ns;
        }

        // Body fat change (max 25): fat decrease is positive
        Map<?, ?> body = (Map<?, ?>) report.get("bodyComposition");
        if (body != null && body.containsKey("bodyFatChangePct")) {
            double fc = toDouble(body.get("bodyFatChangePct"));
            int bs = fc < 0 ? Math.min(25, (int)(-fc * 5)) : 10;
            breakdown.put("bodyFatProgress", bs);
            score += bs;
        } else {
            breakdown.put("bodyFatProgress", 10);
            score += 10;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", score);
        result.put("breakdown", breakdown);
        return result;
    }

    private static String gradeFromScore(int score) {
        if (score >= 90) return "S — 卓越进步";
        if (score >= 75) return "A — 优秀状态";
        if (score >= 60) return "B — 稳步前进";
        if (score >= 45) return "C — 需要加把劲";
        return "D — 重新出发";
    }

    // ──────────────────────────────────────────────────────────────
    // AI summary builder
    // ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String buildAiSummary(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder("【进度摘要】");

        Map<String, Object> ws = (Map<String, Object>) report.get("workoutSummary");
        if (ws != null && toInt(ws.get("sessions")) > 0) {
            sb.append("本期共训练").append(ws.get("sessions")).append("次，");
            if (ws.containsKey("totalCaloriesBurned"))
                sb.append("消耗热量").append(ws.get("totalCaloriesBurned")).append("kcal。");
        } else {
            sb.append("本期无训练记录。");
        }

        Map<String, Object> str = (Map<String, Object>) report.get("streakAnalysis");
        if (str != null) {
            int cs = toInt(str.get("currentStreak"));
            if (cs > 0) sb.append("当前连续打卡").append(cs).append("天。");
        }

        Map<String, Object> bc = (Map<String, Object>) report.get("bodyComposition");
        if (bc != null && bc.containsKey("direction"))
            sb.append(bc.get("direction")).append("。");

        Map<String, Object> cs = (Map<String, Object>) report.get("compositeScore");
        if (cs != null) {
            sb.append("综合评分: ").append(cs.get("total")).append("分 (")
              .append(report.get("progressGrade")).append(")。");
        }

        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private static double toDouble(Object v) {
        if (v == null) return 0;
        try { return ((Number) v).doubleValue(); } catch (ClassCastException e) {
            try { return Double.parseDouble(v.toString()); } catch (NumberFormatException ex) { return 0; }
        }
    }

    private static int toInt(Object v) {
        return (int) Math.round(toDouble(v));
    }

    private static String strVal(Object v, String def) {
        return v != null ? v.toString() : def;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.substring(0, 10)); } catch (Exception e) { return null; }
    }
}
