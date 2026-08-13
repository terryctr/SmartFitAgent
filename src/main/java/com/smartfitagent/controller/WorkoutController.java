package com.smartfitagent.controller;

import com.smartfitagent.service.WorkoutLogService;
import com.smartfitagent.util.BmiCalculator;
import com.smartfitagent.util.WorkoutRecommender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 训练日志 REST 控制器（MVC Controller）
 *
 * 提供训练记录相关 API：
 *  GET  /api/workout/logs           — 查询训练记录列表
 *  POST /api/workout/logs           — 新增训练记录（自动计算热量）
 *  GET  /api/workout/stats          — 训练统计（总次数/分钟/热量/连续天数）
 *  GET  /api/workout/weekly         — 本周训练进度
 *  GET  /api/workout/recommend      — 今日训练推荐
 *  GET  /api/workout/exercises      — 动作数据库查询
 *  GET  /api/workout/exercises/{mg} — 按肌群查询动作
 */
@RestController
@RequestMapping("/api/workout")
@CrossOrigin(origins = "*")
public class WorkoutController {

    @Autowired
    private WorkoutLogService workoutLogService;

    @GetMapping("/logs")
    public ResponseEntity<String> getLogs(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<Map<String, Object>> logs = workoutLogService.getWorkoutLogs(userId, startDate, endDate);
        return ResponseEntity.ok(listToJson(logs));
    }

    @PostMapping("/logs")
    public ResponseEntity<String> saveLog(@RequestBody Map<String, Object> body) {
        String result = workoutLogService.saveWorkoutLog(body);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<String> getStats(
            @RequestParam(defaultValue = "u1001") String userId) {
        Map<String, Object> stats = workoutLogService.getWorkoutStats(userId);
        return ResponseEntity.ok(mapToJson(stats));
    }

    @GetMapping("/weekly")
    public ResponseEntity<String> getWeekly(
            @RequestParam(defaultValue = "u1001") String userId) {
        return ResponseEntity.ok(workoutLogService.weeklyProgressJson(userId));
    }

    @GetMapping("/recommend")
    public ResponseEntity<String> getRecommendation(
            @RequestParam(defaultValue = "22.0") double bmi,
            @RequestParam(defaultValue = "22.0") double targetBmi,
            @RequestParam(defaultValue = "u1001") String userId) {
        return ResponseEntity.ok(workoutLogService.recommendTodayWorkout(userId, bmi, targetBmi));
    }

    @GetMapping("/exercises")
    public ResponseEntity<String> getAllExercises(
            @RequestParam(required = false) String muscleGroup,
            @RequestParam(defaultValue = "INTERMEDIATE") String maxLevel) {
        List<WorkoutRecommender.Exercise> exercises;
        WorkoutRecommender.Difficulty diff;
        try {
            diff = WorkoutRecommender.Difficulty.valueOf(maxLevel.toUpperCase());
        } catch (Exception e) {
            diff = WorkoutRecommender.Difficulty.INTERMEDIATE;
        }

        if (muscleGroup != null && !muscleGroup.isBlank()) {
            try {
                WorkoutRecommender.MuscleGroup mg = WorkoutRecommender.MuscleGroup.valueOf(muscleGroup.toUpperCase());
                exercises = WorkoutRecommender.recommend(mg, diff, 20);
            } catch (Exception e) {
                exercises = WorkoutRecommender.allExercises();
            }
        } else {
            exercises = WorkoutRecommender.allExercises();
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < exercises.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(exercises.get(i).toJson());
        }
        sb.append("]");
        return ResponseEntity.ok(sb.toString());
    }

    @GetMapping("/bmi-report")
    public ResponseEntity<String> getBmiReport(
            @RequestParam double heightCm,
            @RequestParam double weightKg,
            @RequestParam(defaultValue = "匀称") String bodyType) {
        String report = BmiCalculator.report(heightCm, weightKg, bodyType);
        double bmi = BmiCalculator.calculate(heightCm, weightKg);
        double targetBmi = BmiCalculator.targetBmi(bodyType);
        String planSummary = WorkoutRecommender.weeklyPlanSummary(bmi, targetBmi, bodyType);
        return ResponseEntity.ok(
            "{\"report\":\"" + report.replace("\"", "'") + "\","
            + "\"bmi\":" + bmi + ","
            + "\"classification\":\"" + BmiCalculator.classify(bmi) + "\","
            + "\"weeklyPlan\":\"" + planSummary.replace("\"", "'").replace("\n", "\\n") + "\"}"
        );
    }

    private String listToJson(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(mapToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append("\"").append(v.toString().replace("\"", "'")).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
