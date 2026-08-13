package com.smartfitagent.controller;

import com.smartfitagent.service.SleepLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 睡眠日志 REST 控制器
 *
 * 管理睡眠记录的增删查，以及周报统计和改善提示端点。
 */
@RestController
@RequestMapping("/api/sleep")
public class SleepController {

    private final SleepLogService sleepService;

    public SleepController(SleepLogService sleepService) {
        this.sleepService = sleepService;
    }

    /** 记录睡眠 */
    @PostMapping("/log")
    public ResponseEntity<Map<String, Object>> log(@RequestBody Map<String, Object> data) {
        if (!data.containsKey("userId")) data.put("userId", "u1001");
        long id = sleepService.save(data);
        return ResponseEntity.ok(Map.of("id", id, "message", "睡眠记录已保存"));
    }

    /** 获取历史睡眠记录 */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(sleepService.findByUser(userId, Math.min(limit, 90)));
    }

    /** 删除记录 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable long id,
            @RequestParam(defaultValue = "u1001") String userId) {
        boolean ok = sleepService.delete(id, userId);
        return ok ? ResponseEntity.ok(Map.of("message", "已删除"))
                  : ResponseEntity.notFound().build();
    }

    /** 近N天统计周报 */
    @GetMapping("/weekly-stats")
    public ResponseEntity<Map<String, Object>> weeklyStats(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(sleepService.weeklyStats(userId, days));
    }

    /** 最常见干扰因素 */
    @GetMapping("/top-factors")
    public ResponseEntity<List<Map<String, Object>>> topFactors(
            @RequestParam(defaultValue = "u1001") String userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(sleepService.topFactors(userId, days));
    }

    /** 本地生成睡眠改善提示（不调用AI） */
    @GetMapping("/tips")
    public ResponseEntity<Map<String, Object>> getTips(
            @RequestParam(defaultValue = "u1001") String userId) {
        List<String> tips = sleepService.generateLocalTips(userId);
        return ResponseEntity.ok(Map.of("tips", tips, "count", tips.size()));
    }
}
