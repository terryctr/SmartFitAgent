package com.smartfitagent.controller;

import com.smartfitagent.service.NutritionLogService;
import com.smartfitagent.util.CalorieCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 营养追踪 REST 控制器（MVC Controller）
 *
 * API 接口：
 *  POST /api/nutrition/log          — 记录饮食（自动查询热量数据库）
 *  GET  /api/nutrition/daily        — 每日摄入汇总
 *  GET  /api/nutrition/compliance   — 营养合规率报告
 *  GET  /api/nutrition/search       — 食物热量搜索
 *  GET  /api/nutrition/macros       — 宏营养素目标计算
 *  GET  /api/nutrition/calories     — 常见食物热量数据库
 */
@RestController
@RequestMapping("/api/nutrition")
@CrossOrigin(origins = "*")
public class NutritionController {

    @Autowired
    private NutritionLogService nutritionLogService;

    @PostMapping("/log")
    public ResponseEntity<String> logNutrition(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(nutritionLogService.saveNutritionLog(body));
    }

    @GetMapping("/daily")
    public ResponseEntity<String> getDailySummary(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(required = false) String date) {
        String targetDate = date != null ? date : LocalDate.now().toString();
        Map<String, Object> summary = nutritionLogService.getDailySummary(userId, targetDate);
        return ResponseEntity.ok(mapToJson(summary));
    }

    @GetMapping("/compliance")
    public ResponseEntity<String> getCompliance(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "70.0") double weightKg,
            @RequestParam(defaultValue = "maintain") String phase) {
        String targetDate = date != null ? date : LocalDate.now().toString();
        Map<String, Object> report = nutritionLogService.complianceReport(userId, targetDate, weightKg, phase);
        return ResponseEntity.ok(mapToJson(report));
    }

    @GetMapping("/search")
    public ResponseEntity<String> searchFood(@RequestParam String q) {
        return ResponseEntity.ok(nutritionLogService.searchFood(q));
    }

    @GetMapping("/macros")
    public ResponseEntity<String> getMacros(
            @RequestParam double weightKg,
            @RequestParam(defaultValue = "22.0") double bmi,
            @RequestParam(defaultValue = "22.0") double targetBmi,
            @RequestParam(defaultValue = "3") int activityLevel) {
        CalorieCalculator.Phase phase = CalorieCalculator.recommendPhase(bmi, targetBmi);
        double bmr = CalorieCalculator.bmr(weightKg, 170, 25, "male");
        double tdee = CalorieCalculator.tdee(bmr, activityLevel);
        CalorieCalculator.MacroSplit macros = CalorieCalculator.macroSplit(weightKg, tdee, phase);

        return ResponseEntity.ok(
            "{\"macros\":" + macros.toJson() + ","
            + "\"summary\":\"" + macros.summary() + "\","
            + "\"phase\":\"" + phase.name() + "\","
            + "\"bmr\":" + String.format("%.0f", bmr) + ","
            + "\"tdee\":" + String.format("%.0f", tdee) + ","
            + "\"waterMl\":" + CalorieCalculator.dailyWaterMl(weightKg, 400) + "}"
        );
    }

    @GetMapping("/calories")
    public ResponseEntity<String> getFoodDatabase() {
        Map<String, Integer> db = CalorieCalculator.buildFoodDatabase();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Integer> e : db.entrySet()) {
            if (!first) sb.append(",");
            sb.append("{\"name\":\"").append(e.getKey()).append("\","
                + "\"calories\":").append(e.getValue()).append("}");
            first = false;
        }
        sb.append("]");
        return ResponseEntity.ok(sb.toString());
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
            else if (v instanceof String s) sb.append("\"").append(s.replace("\"", "'")).append("\"");
            else sb.append("\"").append(v.toString()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
