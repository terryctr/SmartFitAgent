package com.smartfitagent.controller;

import com.smartfitagent.service.BodyMeasurementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 身体测量 REST 控制器
 *
 * 提供身体围度、体重、体脂数据的 CRUD 及趋势分析端点。
 */
@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

    private final BodyMeasurementService measurementService;

    public MeasurementController(BodyMeasurementService measurementService) {
        this.measurementService = measurementService;
    }

    /** 获取用户测量历史 */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(measurementService.findByUser(userId, Math.min(limit, 100)));
    }

    /** 保存新的测量记录 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> data) {
        if (!data.containsKey("userId")) data.put("userId", "u1001");
        long id = measurementService.save(data);
        return ResponseEntity.ok(Map.of("id", id, "message", "测量记录已保存"));
    }

    /** 获取最新一次测量 */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatest(@RequestParam(defaultValue = "u1001") String userId) {
        Optional<Map<String, Object>> latest = measurementService.findLatest(userId);
        return latest.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 删除测量记录 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable long id,
            @RequestParam(defaultValue = "u1001") String userId) {
        boolean deleted = measurementService.delete(id, userId);
        if (deleted) return ResponseEntity.ok(Map.of("message", "已删除"));
        return ResponseEntity.notFound().build();
    }

    /** 获取趋势分析 */
    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(measurementService.computeTrend(userId, days));
    }

    /** 获取统计摘要 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "u1001") String userId) {
        return ResponseEntity.ok(measurementService.statistics(userId));
    }

    /** 腰臀比分析 */
    @GetMapping("/whr")
    public ResponseEntity<Map<String, Object>> getWhr(
            @RequestParam(defaultValue = "u1001") String userId) {
        return ResponseEntity.ok(measurementService.whrAnalysis(userId));
    }
}
