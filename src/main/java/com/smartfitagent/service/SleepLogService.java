package com.smartfitagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * 睡眠日志服务
 *
 * 记录每日睡眠时长、质量评分和影响因素，
 * 提供睡眠趋势分析和个性化改善建议。
 */
@Service
public class SleepLogService {

    private static final Logger log = LoggerFactory.getLogger(SleepLogService.class);

    private final JdbcTemplate jdbc;

    /** 质量分 -> 描述 */
    private static final Map<Integer, String> QUALITY_LABELS = Map.of(
        1, "很差", 2, "较差", 3, "一般", 4, "良好", 5, "极佳"
    );

    /** 健康睡眠时长下限 */
    private static final double MIN_HEALTHY_HOURS = 7.0;
    /** 健康睡眠时长上限 */
    private static final double MAX_HEALTHY_HOURS = 9.0;

    public SleepLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTableExists();
    }

    // ──────────────────────────────────────────────────
    // Schema bootstrap
    // ──────────────────────────────────────────────────

    private void ensureTableExists() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sleep_logs (
                    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id               VARCHAR(64) NOT NULL,
                    sleep_date            DATE        NOT NULL,
                    bedtime               TIME,
                    wake_time             TIME,
                    duration_hours        DECIMAL(4,2),
                    quality_score         INT         CHECK (quality_score BETWEEN 1 AND 5),
                    wake_ups              INT         DEFAULT 0,
                    sleep_latency_minutes INT         DEFAULT 0,
                    factors               VARCHAR(500),
                    notes                 VARCHAR(1000),
                    created_at            TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_user_date (user_id, sleep_date)
                )
                """);
        } catch (Exception e) {
            log.warn("sleep_logs table init: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────────

    public long save(Map<String, Object> data) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO sleep_logs
                  (user_id,sleep_date,bedtime,wake_time,duration_hours,
                   quality_score,wake_ups,sleep_latency_minutes,factors,notes)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, str(data, "userId", "anonymous"));
            ps.setObject(2, data.getOrDefault("sleepDate", LocalDate.now().toString()));
            ps.setObject(3, data.get("bedtime"));
            ps.setObject(4, data.get("wakeTime"));
            ps.setObject(5, data.get("durationHours"));
            ps.setObject(6, data.getOrDefault("qualityScore", 3));
            ps.setObject(7, data.getOrDefault("wakeUps", 0));
            ps.setObject(8, data.getOrDefault("sleepLatencyMinutes", 0));
            ps.setString(9, str(data, "factors", null));
            ps.setString(10, str(data, "notes", null));
            return ps;
        }, kh);
        long id = Objects.requireNonNull(kh.getKey()).longValue();
        log.info("Sleep log saved id={}", id);
        return id;
    }

    public List<Map<String, Object>> findByUser(String userId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM sleep_logs WHERE user_id=? ORDER BY sleep_date DESC LIMIT ?",
            userId, limit);
    }

    public boolean delete(long id, String userId) {
        return jdbc.update("DELETE FROM sleep_logs WHERE id=? AND user_id=?", id, userId) > 0;
    }

    // ──────────────────────────────────────────────────
    // Analytics
    // ──────────────────────────────────────────────────

    /**
     * 近N天睡眠统计汇总
     */
    public Map<String, Object> weeklyStats(String userId, int days) {
        String cutoff = LocalDate.now().minusDays(days).toString();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM sleep_logs WHERE user_id=? AND sleep_date>=? ORDER BY sleep_date DESC",
            userId, cutoff);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("days", days);
        stats.put("records", rows.size());
        if (rows.isEmpty()) return stats;

        DoubleSummaryStatistics dur = rows.stream()
            .map(r -> r.get("duration_hours"))
            .filter(Objects::nonNull)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .summaryStatistics();

        stats.put("avgHours",  round1(dur.getAverage()));
        stats.put("maxHours",  dur.getMax());
        stats.put("minHours",  dur.getMin());
        stats.put("totalHours", round1(dur.getSum()));

        DoubleSummaryStatistics qual = rows.stream()
            .map(r -> r.get("quality_score"))
            .filter(Objects::nonNull)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .summaryStatistics();
        stats.put("avgQuality", round1(qual.getAverage()));

        long healthyNights = rows.stream()
            .map(r -> r.get("duration_hours"))
            .filter(Objects::nonNull)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .filter(h -> h >= MIN_HEALTHY_HOURS && h <= MAX_HEALTHY_HOURS)
            .count();
        stats.put("healthyNights", healthyNights);
        stats.put("healthyRate",   round1(100.0 * healthyNights / rows.size()));

        // Consistency: lower stddev of bedtime = more consistent
        stats.put("consistencyTip", computeConsistencyTip(rows));

        return stats;
    }

    /**
     * 最常见的睡眠干扰因素 Top-3
     */
    public List<Map<String, Object>> topFactors(String userId, int days) {
        String cutoff = LocalDate.now().minusDays(days).toString();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT factors FROM sleep_logs WHERE user_id=? AND sleep_date>=? AND factors IS NOT NULL",
            userId, cutoff);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String f = (String) r.get("factors");
            if (f == null) continue;
            for (String item : f.split(",")) {
                item = item.trim();
                if (!item.isEmpty()) counts.merge(item, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(e -> Map.of("factor", (Object) e.getKey(), "count", (Object) e.getValue()))
            .toList();
    }

    /**
     * 生成个性化睡眠改善提示（无需AI调用）
     */
    public List<String> generateLocalTips(String userId) {
        Map<String, Object> stats = weeklyStats(userId, 7);
        List<String> tips = new ArrayList<>();

        Double avg = (Double) stats.get("avgHours");
        if (avg != null) {
            if (avg < MIN_HEALTHY_HOURS) {
                tips.add("您近7天平均睡眠仅 " + avg + " 小时，低于推荐的7小时。建议提前30分钟入睡。");
            } else if (avg > MAX_HEALTHY_HOURS) {
                tips.add("您的平均睡眠 " + avg + " 小时略多，过度睡眠也可能增加疲惫感，尝试固定起床时间。");
            } else {
                tips.add("您的睡眠时长 " + avg + " 小时处于健康范围，继续保持！");
            }
        }

        Double q = (Double) stats.get("avgQuality");
        if (q != null && q < 3.0) {
            tips.add("睡眠质量评分偏低（" + q + "/5），建议排查睡前习惯：减少咖啡因摄入，避免晚间高强度训练。");
        }

        List<Map<String, Object>> factors = topFactors(userId, 7);
        if (!factors.isEmpty()) {
            String top = (String) factors.get(0).get("factor");
            tips.add("您最常反映的睡眠干扰因素是「" + top + "」，针对性改善可显著提升睡眠质量。");
        }

        if (tips.isEmpty()) {
            tips.add("坚持记录睡眠数据，积累更多数据后将生成个性化分析。");
        }
        return tips;
    }

    // ──────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────

    private String computeConsistencyTip(List<Map<String, Object>> rows) {
        long consistentRows = rows.stream()
            .filter(r -> r.get("bedtime") != null)
            .count();
        if (consistentRows < 3) return "数据不足，请持续记录";
        return "作息" + (consistentRows >= 5 ? "较规律" : "有待改善") + "，建议每天固定入睡时间";
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }
}
