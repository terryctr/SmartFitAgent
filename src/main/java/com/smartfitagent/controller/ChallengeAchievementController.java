package com.smartfitagent.controller;

import com.smartfitagent.service.AchievementService;
import com.smartfitagent.service.ChallengeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 挑战赛与成就 REST 控制器（MVC Controller）
 *
 * API 接口：
 *  GET  /api/challenges                     — 活跃挑战列表
 *  GET  /api/challenges/user                — 用户已参加的挑战
 *  POST /api/challenges/{id}/join           — 参加挑战
 *  POST /api/challenges/{id}/progress       — 更新挑战进度
 *  GET  /api/challenges/{id}/leaderboard    — 挑战排行榜
 *  GET  /api/challenges/recommend           — 个性化挑战推荐
 *  GET  /api/achievements                   — 用户成就列表
 *  GET  /api/achievements/all               — 全部成就定义
 *  POST /api/achievements/check             — 触发成就检查
 */
@RestController
@CrossOrigin(origins = "*")
public class ChallengeAchievementController {

    @Autowired
    private ChallengeService challengeService;

    @Autowired
    private AchievementService achievementService;

    // ── Challenges ────────────────────────────────────────────────────────────

    @GetMapping("/api/challenges")
    public ResponseEntity<String> getActiveChallenges() {
        return ResponseEntity.ok(challengeService.getActiveChallengesJson());
    }

    @GetMapping("/api/challenges/user")
    public ResponseEntity<String> getUserChallenges(
            @RequestParam(defaultValue = "u1001") String userId) {
        return ResponseEntity.ok(challengeService.getUserChallengesJson(userId));
    }

    @PostMapping("/api/challenges/{id}/join")
    public ResponseEntity<String> joinChallenge(
            @PathVariable("id") String challengeId,
            @RequestParam(defaultValue = "u1001") String userId) {
        return ResponseEntity.ok(challengeService.joinChallenge(userId, challengeId));
    }

    @PostMapping("/api/challenges/{id}/progress")
    public ResponseEntity<String> updateProgress(
            @PathVariable("id") String challengeId,
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestBody Map<String, Object> body) {
        double value = toDouble(body.getOrDefault("value", 0.0));
        return ResponseEntity.ok(challengeService.updateProgress(userId, challengeId, value));
    }

    @GetMapping("/api/challenges/{id}/leaderboard")
    public ResponseEntity<String> getLeaderboard(@PathVariable("id") String challengeId) {
        return ResponseEntity.ok(challengeService.getLeaderboardJson(challengeId));
    }

    @GetMapping("/api/challenges/recommend")
    public ResponseEntity<String> recommend(
            @RequestParam(defaultValue = "22.0") double bmi,
            @RequestParam(defaultValue = "减脂与力量并行") String mode) {
        return ResponseEntity.ok(challengeService.recommendChallenges(bmi, mode));
    }

    // ── Achievements ──────────────────────────────────────────────────────────

    @GetMapping("/api/achievements")
    public ResponseEntity<String> getUserAchievements(
            @RequestParam(defaultValue = "u1001") String userId) {
        return ResponseEntity.ok(achievementService.getUserAchievementsJson(userId));
    }

    @GetMapping("/api/achievements/all")
    public ResponseEntity<String> getAllAchievements() {
        return ResponseEntity.ok(achievementService.getAllDefinitionsJson());
    }

    @PostMapping("/api/achievements/check")
    public ResponseEntity<String> checkAchievements(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(defaultValue = "0") int totalWorkouts,
            @RequestParam(defaultValue = "0") int streakDays,
            @RequestParam(defaultValue = "0") int nutritionLogs,
            @RequestParam(defaultValue = "false") boolean hasProfile,
            @RequestParam(defaultValue = "false") boolean hasPlan) {
        var newAchievements = achievementService.checkAndAwardAchievements(
                userId, totalWorkouts, streakDays, nutritionLogs, hasProfile, hasPlan);
        String json = "{\"newAchievements\":["
            + newAchievements.stream().map(a -> "\"" + a + "\"")
                .reduce((a, b) -> a + "," + b).orElse("")
            + "],\"count\":" + newAchievements.size() + "}";
        return ResponseEntity.ok(json);
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
}
