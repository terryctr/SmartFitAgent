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
 * 身体测量数据服务
 *
 * 管理用户的围度、体重、体脂率等身体成分数据，
 * 支持趋势分析和体型评估。
 */
@Service
public class BodyMeasurementService {

    private static final Logger log = LoggerFactory.getLogger(BodyMeasurementService.class);

    private final JdbcTemplate jdbc;

    public BodyMeasurementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTableExists();
    }

    // ──────────────────────────────────────────────────
    // Schema bootstrap
    // ──────────────────────────────────────────────────

    private void ensureTableExists() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS body_measurements (
                    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id       VARCHAR(64)  NOT NULL,
                    measure_date  DATE         NOT NULL,
                    weight_kg     DECIMAL(5,2),
                    body_fat_pct  DECIMAL(4,1),
                    muscle_mass_kg DECIMAL(5,2),
                    chest_cm      DECIMAL(5,1),
                    waist_cm      DECIMAL(5,1),
                    hip_cm        DECIMAL(5,1),
                    arm_cm        DECIMAL(5,1),
                    thigh_cm      DECIMAL(5,1),
                    calf_cm       DECIMAL(5,1),
                    neck_cm       DECIMAL(5,1),
                    notes         VARCHAR(500),
                    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_user_date (user_id, measure_date)
                )
                """);
        } catch (Exception e) {
            log.warn("body_measurements table init: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────────

    public long save(Map<String, Object> data) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO body_measurements
                  (user_id,measure_date,weight_kg,body_fat_pct,muscle_mass_kg,
                   chest_cm,waist_cm,hip_cm,arm_cm,thigh_cm,calf_cm,neck_cm,notes)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, str(data, "userId", "anonymous"));
            ps.setObject(2, data.getOrDefault("measureDate", LocalDate.now().toString()));
            ps.setObject(3, data.get("weightKg"));
            ps.setObject(4, data.get("bodyFatPct"));
            ps.setObject(5, data.get("muscleMassKg"));
            ps.setObject(6, data.get("chestCm"));
            ps.setObject(7, data.get("waistCm"));
            ps.setObject(8, data.get("hipCm"));
            ps.setObject(9, data.get("armCm"));
            ps.setObject(10, data.get("thighCm"));
            ps.setObject(11, data.get("calfCm"));
            ps.setObject(12, data.get("neckCm"));
            ps.setString(13, str(data, "notes", null));
            return ps;
        }, kh);
        long id = Objects.requireNonNull(kh.getKey()).longValue();
        log.info("Body measurement saved id={}", id);
        return id;
    }

    public List<Map<String, Object>> findByUser(String userId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM body_measurements WHERE user_id=? ORDER BY measure_date DESC LIMIT ?",
            userId, limit);
    }

    public Optional<Map<String, Object>> findLatest(String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM body_measurements WHERE user_id=? ORDER BY measure_date DESC LIMIT 1", userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public boolean delete(long id, String userId) {
        int rows = jdbc.update("DELETE FROM body_measurements WHERE id=? AND user_id=?", id, userId);
        return rows > 0;
    }

    // ──────────────────────────────────────────────────
    // Analytics
    // ──────────────────────────────────────────────────

    /**
     * 计算给定时间段内每个指标的变化量（最新 - 最早）
     */
    public Map<String, Object> computeTrend(String userId, int days) {
        String cutoff = LocalDate.now().minusDays(days).toString();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM body_measurements WHERE user_id=? AND measure_date >= ? ORDER BY measure_date ASC",
            userId, cutoff);

        Map<String, Object> trend = new LinkedHashMap<>();
        if (rows.size() < 2) {
            trend.put("dataPoints", rows.size());
            trend.put("message", "需要至少2次测量记录才能计算趋势");
            return trend;
        }

        Map<String, Object> first = rows.get(0);
        Map<String, Object> last  = rows.get(rows.size() - 1);

        trend.put("dataPoints", rows.size());
        trend.put("daysCovered", days);
        trend.put("weightChange",   diff(first, last, "weight_kg"));
        trend.put("bodyFatChange",  diff(first, last, "body_fat_pct"));
        trend.put("muscleChange",   diff(first, last, "muscle_mass_kg"));
        trend.put("waistChange",    diff(first, last, "waist_cm"));
        trend.put("chestChange",    diff(first, last, "chest_cm"));
        trend.put("armChange",      diff(first, last, "arm_cm"));

        // Composite score: weight, fat down = positive; muscle, chest, arm up = positive
        double score = 0;
        Double wc = (Double) trend.get("weightChange");
        Double fc = (Double) trend.get("bodyFatChange");
        Double mc = (Double) trend.get("muscleChange");
        if (wc != null) score -= wc * 2;
        if (fc != null) score -= fc * 3;
        if (mc != null) score += mc * 4;
        trend.put("compositeProgressScore", Math.round(score * 10.0) / 10.0);

        return trend;
    }

    /**
     * 生成最近N次测量的统计摘要
     */
    public Map<String, Object> statistics(String userId) {
        List<Map<String, Object>> rows = findByUser(userId, 90);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRecords", rows.size());
        if (rows.isEmpty()) return stats;

        OptionalDouble avgWeight = rows.stream()
            .map(r -> r.get("weight_kg"))
            .filter(Objects::nonNull)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .average();
        avgWeight.ifPresent(v -> stats.put("avgWeightKg", Math.round(v * 10.0) / 10.0));

        OptionalDouble avgFat = rows.stream()
            .map(r -> r.get("body_fat_pct"))
            .filter(Objects::nonNull)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .average();
        avgFat.ifPresent(v -> stats.put("avgBodyFatPct", Math.round(v * 10.0) / 10.0));

        OptionalDouble minWeight = rows.stream()
            .map(r -> r.get("weight_kg"))
            .filter(Objects::nonNull)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .min();
        minWeight.ifPresent(v -> stats.put("minWeightKg", v));

        OptionalDouble maxWeight = rows.stream()
            .map(r -> r.get("weight_kg"))
            .filter(Objects::nonNull)
            .mapToDouble(v -> ((Number) v).doubleValue())
            .max();
        maxWeight.ifPresent(v -> stats.put("maxWeightKg", v));

        return stats;
    }

    /**
     * 腰臀比 WHR 分析
     */
    public Map<String, Object> whrAnalysis(String userId) {
        Optional<Map<String, Object>> latest = findLatest(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (latest.isEmpty()) { result.put("available", false); return result; }

        Map<String, Object> m = latest.get();
        Object waist = m.get("waist_cm");
        Object hip   = m.get("hip_cm");
        if (waist == null || hip == null) { result.put("available", false); return result; }

        double w = ((Number) waist).doubleValue();
        double h = ((Number) hip).doubleValue();
        double whr = Math.round(w / h * 100.0) / 100.0;
        result.put("available", true);
        result.put("waistCm", w);
        result.put("hipCm", h);
        result.put("whr", whr);
        // Chinese standard thresholds
        result.put("riskLevel", whr < 0.80 ? "低风险" : whr < 0.85 ? "中等风险" : "高风险");
        result.put("note", "腰臀比 <0.80(女)/0.90(男) 为健康范围");
        return result;
    }

    // ──────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────

    private Double diff(Map<String, Object> first, Map<String, Object> last, String col) {
        Object a = first.get(col);
        Object b = last.get(col);
        if (a == null || b == null) return null;
        double delta = ((Number) b).doubleValue() - ((Number) a).doubleValue();
        return Math.round(delta * 10.0) / 10.0;
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }
}
