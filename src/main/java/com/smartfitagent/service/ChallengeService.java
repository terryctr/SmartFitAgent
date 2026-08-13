package com.smartfitagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 挑战赛服务层
 *
 * 管理健身挑战的完整生命周期：
 *  - 获取活跃挑战列表
 *  - 用户参与挑战
 *  - 更新挑战进度（打卡）
 *  - 挑战完成判定
 *  - 个性化挑战推荐（基于 BMI 和训练偏好）
 *
 * 挑战类型：
 *  STRENGTH  — 力量训练（深蹲、引体向上）
 *  CARDIO    — 有氧运动（万步走、慢跑）
 *  NUTRITION — 饮食挑战（蛋白质达标、无糖）
 *  HABIT     — 习惯培养（早起晨练、睡前拉伸）
 *  COMBINED  — 综合挑战
 */
@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);

    @Autowired
    private JdbcTemplate jdbc;

    // ── Get challenges ────────────────────────────────────────────────────────

    public String getActiveChallengesJson() {
        try {
            List<Map<String, Object>> challenges = jdbc.queryForList(
                "SELECT * FROM challenges WHERE status = '进行中' ORDER BY reward_points DESC LIMIT 20"
            );
            return listToJson(challenges);
        } catch (Exception e) {
            log.debug("挑战列表查询失败，返回默认: {}", e.getMessage());
            return getDefaultChallengesJson();
        }
    }

    private String getDefaultChallengesJson() {
        return "[{\"id\":\"ch001\",\"title\":\"30天深蹲挑战\","
            + "\"description\":\"坚持30天每日深蹲，从50个开始逐渐增加到100个\","
            + "\"challengeType\":\"STRENGTH\",\"targetValue\":30,\"targetUnit\":\"天\","
            + "\"rewardPoints\":100,\"status\":\"进行中\"},"
            + "{\"id\":\"ch002\",\"title\":\"每日万步挑战\","
            + "\"description\":\"连续21天每天完成10000步\","
            + "\"challengeType\":\"CARDIO\",\"targetValue\":21,\"targetUnit\":\"天\","
            + "\"rewardPoints\":80,\"status\":\"进行中\"},"
            + "{\"id\":\"ch003\",\"title\":\"蛋白质摄入挑战\","
            + "\"description\":\"连续14天每日蛋白质摄入达标\","
            + "\"challengeType\":\"NUTRITION\",\"targetValue\":14,\"targetUnit\":\"天\","
            + "\"rewardPoints\":60,\"status\":\"进行中\"}]";
    }

    // ── User participation ────────────────────────────────────────────────────

    public String joinChallenge(String userId, String challengeId) {
        String id = "uc" + System.currentTimeMillis();
        try {
            // Check if already joined
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_challenges WHERE user_id = ? AND challenge_id = ?",
                Integer.class, userId, challengeId);
            if (count != null && count > 0) {
                return "{\"status\":\"already_joined\",\"message\":\"您已参加此挑战\"}";
            }
            jdbc.update(
                "INSERT INTO user_challenges (id, user_id, challenge_id, current_value, completed) "
                + "VALUES (?, ?, ?, 0, FALSE)",
                id, userId, challengeId
            );
            log.info("用户 {} 参加挑战 {}", userId, challengeId);
            return "{\"status\":\"joined\",\"id\":\"" + id + "\",\"message\":\"成功参加挑战！\"}";
        } catch (Exception e) {
            log.debug("参加挑战数据库操作失败: {}", e.getMessage());
            return "{\"status\":\"joined\",\"id\":\"" + id + "\",\"message\":\"已记录参与（演示模式）\"}";
        }
    }

    public String updateProgress(String userId, String challengeId, double newValue) {
        try {
            jdbc.update(
                "UPDATE user_challenges SET current_value = ? WHERE user_id = ? AND challenge_id = ?",
                newValue, userId, challengeId
            );

            // Check if completed
            List<Map<String, Object>> challenge = jdbc.queryForList(
                "SELECT target_value FROM challenges WHERE id = ?", challengeId);
            if (!challenge.isEmpty()) {
                double target = toDouble(challenge.get(0).get("target_value"));
                if (newValue >= target) {
                    jdbc.update(
                        "UPDATE user_challenges SET completed = TRUE, completed_at = ? "
                        + "WHERE user_id = ? AND challenge_id = ?",
                        LocalDate.now().toString(), userId, challengeId
                    );
                    return "{\"status\":\"completed\",\"message\":\"🎉 挑战完成！\","
                           + "\"currentValue\":" + newValue + "}";
                }
            }
            return "{\"status\":\"updated\",\"currentValue\":" + newValue
                   + ",\"message\":\"进度已更新\"}";
        } catch (Exception e) {
            return "{\"status\":\"updated\",\"currentValue\":" + newValue + "}";
        }
    }

    public String getUserChallengesJson(String userId) {
        try {
            List<Map<String, Object>> userChallenges = jdbc.queryForList(
                "SELECT uc.*, c.title, c.description, c.target_value, c.target_unit, "
                + "c.reward_points, c.challenge_type "
                + "FROM user_challenges uc JOIN challenges c ON uc.challenge_id = c.id "
                + "WHERE uc.user_id = ? ORDER BY uc.joined_at DESC",
                userId
            );
            return listToJson(userChallenges);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ── Smart challenge recommendation ───────────────────────────────────────

    /**
     * 基于 BMI、训练偏好推荐最适合的挑战
     */
    public String recommendChallenges(double bmi, String trainingMode) {
        List<String> recs = new ArrayList<>();

        // 总是推荐饮食挑战（普适性强）
        recs.add("{\"title\":\"蛋白质摄入挑战\","
                + "\"reason\":\"均衡蛋白质摄入对所有训练目标都有帮助\","
                + "\"difficulty\":\"易\",\"points\":60}");

        if (bmi > 25) {
            recs.add("{\"title\":\"每日万步挑战\","
                    + "\"reason\":\"BMI偏高，有氧运动帮助提升代谢、消耗热量\","
                    + "\"difficulty\":\"中\",\"points\":80}");
        }

        if (trainingMode != null && (trainingMode.contains("力量") || trainingMode.contains("增肌"))) {
            recs.add("{\"title\":\"30天深蹲挑战\","
                    + "\"reason\":\"深蹲是增肌训练基础，渐进增加强度\","
                    + "\"difficulty\":\"中\",\"points\":100}");
        } else {
            recs.add("{\"title\":\"早起晨练挑战\","
                    + "\"reason\":\"建立晨练习惯，提升全天精力和代谢率\","
                    + "\"difficulty\":\"难\",\"points\":50}");
        }

        return "[" + String.join(",", recs) + "]";
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    public String getLeaderboardJson(String challengeId) {
        try {
            List<Map<String, Object>> lb = jdbc.queryForList(
                "SELECT uc.user_id, uc.current_value, uc.completed, "
                + "uc.joined_at FROM user_challenges uc "
                + "WHERE uc.challenge_id = ? ORDER BY uc.current_value DESC LIMIT 20",
                challengeId
            );
            return listToJson(lb);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String listToJson(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return "[]";
        List<String> items = new ArrayList<>();
        for (Map<String, Object> row : list) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v == null) sb.append("null");
                else if (v instanceof Number) sb.append(v);
                else if (v instanceof Boolean) sb.append(v);
                else sb.append("\"").append(v.toString().replace("\"", "'")).append("\"");
                first = false;
            }
            sb.append("}");
            items.add(sb.toString());
        }
        return "[" + String.join(",", items) + "]";
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
}
