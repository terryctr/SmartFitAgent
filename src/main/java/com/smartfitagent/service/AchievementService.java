package com.smartfitagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 成就系统服务层
 *
 * 实现游戏化健身激励机制：
 *  - 触发条件检测（训练次数、连续打卡天数、达到里程碑）
 *  - 自动颁发成就徽章
 *  - 积分累积和排名
 *  - 成就解锁通知
 *
 * 成就类型：
 *  STREAK   — 连续打卡类（3天、7天、30天）
 *  VOLUME   — 总量里程碑（10次训练、50次训练）
 *  GOAL     — 目标达成类（达到目标 BMI）
 *  SOCIAL   — 社交类（参与挑战、分享）
 *  SPECIAL  — 特殊类（完美一周：5天训练+7天饮食记录）
 */
@Service
public class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    @Autowired
    private JdbcTemplate jdbc;

    // ── Achievement definitions ───────────────────────────────────────────────

    public record AchievementDef(String id, String title, String description,
                                  String badgeType, int points) {}

    private static final List<AchievementDef> ALL_ACHIEVEMENTS = List.of(
        // Streak achievements
        new AchievementDef("streak_3",  "三日不辍", "连续3天记录训练",          "STREAK",  30),
        new AchievementDef("streak_7",  "一周坚持", "连续7天记录训练",           "STREAK",  70),
        new AchievementDef("streak_14", "两周坚持", "连续14天记录训练",          "STREAK", 150),
        new AchievementDef("streak_30", "月度冠军", "连续30天记录训练",          "STREAK", 300),

        // Volume achievements
        new AchievementDef("log_5",   "初露锋芒", "完成5次训练记录",            "VOLUME",  50),
        new AchievementDef("log_20",  "持之以恒", "完成20次训练记录",           "VOLUME", 100),
        new AchievementDef("log_50",  "训练达人", "完成50次训练记录",           "VOLUME", 200),
        new AchievementDef("log_100", "百次勇士", "完成100次训练记录",          "VOLUME", 500),

        // Nutrition achievements
        new AchievementDef("nutrition_7",  "饮食自律", "连续7天记录饮食",        "NUTRITION", 80),
        new AchievementDef("protein_goal", "蛋白质达标", "单日蛋白质摄入达标",  "NUTRITION", 20),

        // Goal achievements
        new AchievementDef("bmi_normal",  "健康体重", "BMI 进入正常范围",        "GOAL", 200),
        new AchievementDef("first_plan",  "计划先行", "创建第一个训练计划",      "GOAL",  50),
        new AchievementDef("profile_set", "建立档案", "完善个人健身档案",        "GOAL",  30),

        // Challenge achievements
        new AchievementDef("first_challenge", "勇于挑战", "参加第一个挑战",     "CHALLENGE", 50),
        new AchievementDef("challenge_done",  "挑战完成", "成功完成一个挑战",   "CHALLENGE", 100),

        // Special achievements
        new AchievementDef("perfect_week", "完美一周",
                "一周内完成5次训练且7天记录饮食", "SPECIAL", 400),
        new AchievementDef("early_bird", "晨练达人",
                "连续5天在早上8点前完成训练",     "SPECIAL", 150)
    );

    // ── Check and award achievements ──────────────────────────────────────────

    public List<String> checkAndAwardAchievements(String userId,
                                                    int totalWorkouts,
                                                    int streakDays,
                                                    int nutritionLogs,
                                                    boolean hasProfile,
                                                    boolean hasPlan) {
        List<String> newlyEarned = new ArrayList<>();

        Map<String, Boolean> conditions = new LinkedHashMap<>();
        conditions.put("streak_3",    streakDays >= 3);
        conditions.put("streak_7",    streakDays >= 7);
        conditions.put("streak_14",   streakDays >= 14);
        conditions.put("streak_30",   streakDays >= 30);
        conditions.put("log_5",       totalWorkouts >= 5);
        conditions.put("log_20",      totalWorkouts >= 20);
        conditions.put("log_50",      totalWorkouts >= 50);
        conditions.put("log_100",     totalWorkouts >= 100);
        conditions.put("nutrition_7", nutritionLogs >= 7);
        conditions.put("profile_set", hasProfile);
        conditions.put("first_plan",  hasPlan);

        Set<String> alreadyEarned = getEarnedAchievementIds(userId);

        for (AchievementDef def : ALL_ACHIEVEMENTS) {
            if (alreadyEarned.contains(def.id())) continue;
            Boolean condition = conditions.get(def.id());
            if (condition != null && condition) {
                awardAchievement(userId, def);
                newlyEarned.add(def.title());
                log.info("颁发成就给 {}: {} (+{}分)", userId, def.title(), def.points());
            }
        }

        return newlyEarned;
    }

    private void awardAchievement(String userId, AchievementDef def) {
        try {
            String existCheck = "SELECT COUNT(*) FROM achievements WHERE user_id = ? AND title = ?";
            Integer count = jdbc.queryForObject(existCheck, Integer.class, userId, def.title());
            if (count != null && count > 0) return;

            jdbc.update(
                "INSERT INTO achievements (id, user_id, title, description, badge_type, "
                + "points_earned, earned_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ach" + System.currentTimeMillis() + "_" + def.id(),
                userId, def.title(), def.description(), def.badgeType(),
                def.points(), LocalDateTime.now().toString()
            );
        } catch (Exception e) {
            log.debug("成就写入失败: {}", e.getMessage());
        }
    }

    private Set<String> getEarnedAchievementIds(String userId) {
        try {
            List<String> titles = jdbc.queryForList(
                "SELECT title FROM achievements WHERE user_id = ?", String.class, userId);
            return new HashSet<>(titles);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    // ── Get achievements ──────────────────────────────────────────────────────

    public String getUserAchievementsJson(String userId) {
        try {
            List<Map<String, Object>> earned = jdbc.queryForList(
                "SELECT * FROM achievements WHERE user_id = ? ORDER BY earned_at DESC", userId);
            int totalPoints = earned.stream().mapToInt(a -> toInt(a.get("points_earned"))).sum();

            StringBuilder sb = new StringBuilder();
            sb.append("{\"userId\":\"").append(userId).append("\",");
            sb.append("\"totalPoints\":").append(totalPoints).append(",");
            sb.append("\"earnedCount\":").append(earned.size()).append(",");
            sb.append("\"totalAvailable\":").append(ALL_ACHIEVEMENTS.size()).append(",");
            sb.append("\"achievements\":[");
            for (int i = 0; i < earned.size(); i++) {
                if (i > 0) sb.append(",");
                Map<String, Object> a = earned.get(i);
                sb.append("{\"title\":\"").append(a.get("title")).append("\",");
                sb.append("\"description\":\"").append(a.get("description")).append("\",");
                sb.append("\"badge\":\"").append(a.get("badge_type")).append("\",");
                sb.append("\"points\":").append(a.get("points_earned")).append(",");
                sb.append("\"earnedAt\":\"").append(a.get("earned_at")).append("\"}");
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"userId\":\"" + userId + "\",\"totalPoints\":0,\"achievements\":[]}";
        }
    }

    public String getAllDefinitionsJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ALL_ACHIEVEMENTS.size(); i++) {
            if (i > 0) sb.append(",");
            AchievementDef d = ALL_ACHIEVEMENTS.get(i);
            sb.append("{\"id\":\"").append(d.id()).append("\",");
            sb.append("\"title\":\"").append(d.title()).append("\",");
            sb.append("\"description\":\"").append(d.description()).append("\",");
            sb.append("\"badge\":\"").append(d.badgeType()).append("\",");
            sb.append("\"points\":").append(d.points()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
