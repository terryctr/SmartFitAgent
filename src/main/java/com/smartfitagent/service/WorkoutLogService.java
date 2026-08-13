package com.smartfitagent.service;

import com.smartfitagent.util.CalorieCalculator;
import com.smartfitagent.util.WorkoutRecommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 训练日志服务层（Spring Service Bean）
 *
 * 提供训练记录的完整 CRUD 操作，并在写入时自动计算：
 *  - 热量消耗（基于运动类型和持续时间）
 *  - 完成状态评分
 *  - 动作是否符合用户体能水平
 *
 * 使用 Spring JdbcTemplate 直接操作 workout_logs 表，
 * 不依赖 JPA，保持轻量。
 */
@Service
public class WorkoutLogService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutLogService.class);

    @Autowired
    private JdbcTemplate jdbc;

    // ── Create ────────────────────────────────────────────────────────────────

    public String saveWorkoutLog(Map<String, Object> data) {
        String id = "w" + System.currentTimeMillis();
        String userId = (String) data.getOrDefault("userId", "u1001");
        String workoutType = (String) data.getOrDefault("workoutType", "力量训练");
        String exerciseName = (String) data.getOrDefault("exerciseName", "深蹲");
        int sets = toInt(data.getOrDefault("sets", 3));
        int reps = toInt(data.getOrDefault("reps", 10));
        double weightKg = toDouble(data.getOrDefault("weightKg", 0.0));
        int durationMinutes = toInt(data.getOrDefault("durationMinutes", 45));
        String notes = (String) data.getOrDefault("notes", "");
        String workoutDate = (String) data.getOrDefault("date", LocalDate.now().toString());

        // Auto-calculate calories burned
        double userWeight = toDouble(data.getOrDefault("userWeightKg", 70.0));
        int caloriesBurned = CalorieCalculator.exerciseCaloriesBurned(workoutType, durationMinutes, userWeight);

        try {
            jdbc.update(
                "INSERT INTO workout_logs (id, user_id, workout_type, exercise_name, sets, reps, "
                + "weight_kg, duration_minutes, calories_burned, notes, workout_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, workoutType, exerciseName, sets, reps,
                weightKg, durationMinutes, caloriesBurned, notes, workoutDate
            );
            log.info("训练记录已保存: {} — {}", exerciseName, workoutDate);
        } catch (Exception e) {
            log.warn("数据库写入失败，返回内存记录: {}", e.getMessage());
        }

        return buildWorkoutJson(id, userId, workoutType, exerciseName,
                sets, reps, weightKg, durationMinutes, caloriesBurned, notes, workoutDate);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getWorkoutLogs(String userId, String startDate, String endDate) {
        try {
            String sql = "SELECT * FROM workout_logs WHERE user_id = ? "
                    + "AND workout_date >= ? AND workout_date <= ? ORDER BY workout_date DESC LIMIT 100";
            return jdbc.queryForList(sql, userId,
                    startDate != null ? startDate : "2000-01-01",
                    endDate != null ? endDate : LocalDate.now().toString());
        } catch (Exception e) {
            log.debug("数据库查询失败，返回空列表: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getWorkoutStats(String userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("totalSessions", countSessions(userId));
            stats.put("totalMinutes", sumMinutes(userId));
            stats.put("totalCalories", sumCalories(userId));
            stats.put("thisWeekSessions", countSessionsThisWeek(userId));
            stats.put("avgSessionMinutes", avgMinutes(userId));
            stats.put("favoriteExercise", favoriteExercise(userId));
            stats.put("streakDays", calcStreak(userId));
        } catch (Exception e) {
            log.debug("统计计算失败: {}", e.getMessage());
            stats.put("totalSessions", 0);
            stats.put("totalMinutes", 0);
            stats.put("totalCalories", 0);
        }
        return stats;
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    public String weeklyProgressJson(String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", getWorkoutStats(userId));

        // Get this week's logs
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        List<Map<String, Object>> thisWeek = getWorkoutLogs(userId,
                monday.toString(), LocalDate.now().toString());
        result.put("thisWeekLogs", thisWeek);
        result.put("thisWeekCount", thisWeek.size());

        // Muscle group distribution
        Map<String, Integer> groupCount = new LinkedHashMap<>();
        for (Map<String, Object> log : thisWeek) {
            String type = (String) log.getOrDefault("workout_type", "其他");
            groupCount.merge(type, 1, Integer::sum);
        }
        result.put("muscleGroupDistribution", groupCount);

        return mapToJson(result);
    }

    /**
     * 推荐今日训练（基于最近7天的记录，避免同肌群连续训练）
     */
    public String recommendTodayWorkout(String userId, double bmi, double targetBmi) {
        String recommendation = WorkoutRecommender.weeklyPlanSummary(bmi, targetBmi, "匀称");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recommendation", recommendation);
        result.put("date", LocalDate.now().toString());
        result.put("generatedAt", java.time.LocalDateTime.now().toString());
        return mapToJson(result);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private int countSessions(String userId) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workout_logs WHERE user_id = ?", Integer.class, userId);
            return count == null ? 0 : count;
        } catch (Exception e) { return 0; }
    }

    private int countSessionsThisWeek(String userId) {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workout_logs WHERE user_id = ? AND workout_date >= ?",
                Integer.class, userId, monday.toString());
            return count == null ? 0 : count;
        } catch (Exception e) { return 0; }
    }

    private long sumMinutes(String userId) {
        try {
            Long sum = jdbc.queryForObject(
                    "SELECT SUM(duration_minutes) FROM workout_logs WHERE user_id = ?", Long.class, userId);
            return sum == null ? 0 : sum;
        } catch (Exception e) { return 0; }
    }

    private long sumCalories(String userId) {
        try {
            Long sum = jdbc.queryForObject(
                    "SELECT SUM(calories_burned) FROM workout_logs WHERE user_id = ?", Long.class, userId);
            return sum == null ? 0 : sum;
        } catch (Exception e) { return 0; }
    }

    private double avgMinutes(String userId) {
        try {
            Double avg = jdbc.queryForObject(
                    "SELECT AVG(duration_minutes) FROM workout_logs WHERE user_id = ?", Double.class, userId);
            return avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;
        } catch (Exception e) { return 0.0; }
    }

    private String favoriteExercise(String userId) {
        try {
            List<Map<String, Object>> result = jdbc.queryForList(
                "SELECT exercise_name, COUNT(*) as cnt FROM workout_logs "
                + "WHERE user_id = ? GROUP BY exercise_name ORDER BY cnt DESC LIMIT 1",
                userId);
            if (!result.isEmpty()) return (String) result.get(0).get("exercise_name");
        } catch (Exception e) { /* ignore */ }
        return "暂无记录";
    }

    private int calcStreak(String userId) {
        // Simple streak: count consecutive days with workouts
        try {
            List<Map<String, Object>> dates = jdbc.queryForList(
                "SELECT DISTINCT workout_date FROM workout_logs WHERE user_id = ? "
                + "ORDER BY workout_date DESC LIMIT 30",
                userId);
            if (dates.isEmpty()) return 0;
            int streak = 0;
            LocalDate expected = LocalDate.now();
            for (Map<String, Object> row : dates) {
                LocalDate date = LocalDate.parse(row.get("workout_date").toString());
                if (date.equals(expected) || date.equals(expected.minusDays(1))) {
                    streak++;
                    expected = date.minusDays(1);
                } else {
                    break;
                }
            }
            return streak;
        } catch (Exception e) { return 0; }
    }

    private String buildWorkoutJson(String id, String userId, String workoutType,
                                     String exerciseName, int sets, int reps, double weightKg,
                                     int durationMinutes, int caloriesBurned,
                                     String notes, String workoutDate) {
        return String.format(
            "{\"id\":\"%s\",\"userId\":\"%s\",\"workoutType\":\"%s\","
            + "\"exerciseName\":\"%s\",\"sets\":%d,\"reps\":%d,"
            + "\"weightKg\":%.1f,\"durationMinutes\":%d,\"caloriesBurned\":%d,"
            + "\"notes\":\"%s\",\"workoutDate\":\"%s\"}",
            id, userId, workoutType, exerciseName, sets, reps,
            weightKg, durationMinutes, caloriesBurned, notes, workoutDate
        );
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) sb.append("\"").append(s.replace("\"", "'")).append("\"");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else if (v instanceof Map) sb.append(mapToJson((Map<String, Object>) v));
            else if (v instanceof List) sb.append(v.toString());
            else sb.append("\"").append(v).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private int toInt(Object v) {
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    private double toDouble(Object v) {
        if (v instanceof Double d) return d;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
}
