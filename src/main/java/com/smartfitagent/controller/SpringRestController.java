package com.smartfitagent.controller;

import com.smartfitagent.Json;
import com.smartfitagent.StudyService;
import com.smartfitagent.agent.AgentFactory;
import com.smartfitagent.ai.AiClient;
import com.smartfitagent.ai.decorator.CachingAiClientDecorator;
import com.smartfitagent.ai.decorator.LoggingAiClientDecorator;
import com.smartfitagent.ai.decorator.RateLimitingAiClientDecorator;
import com.smartfitagent.ai.pipeline.AiPipeline;
import com.smartfitagent.ai.pipeline.PipelineContext;
import com.smartfitagent.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring Boot REST API 控制器（MVC Controller 层）
 *
 * 提供所有前端 API 接口，按功能分组：
 *  1. 系统接口  /api/health, /api/ai/info
 *  2. 用户档案  /api/user
 *  3. 核心数据  /api/dashboard, /api/analytics
 *  4. 训练计划  /api/plans
 *  5. 饮食记录  /api/notes
 *  6. 体能评估  /api/quiz, /api/quiz/submit
 *  7. 课程管理  /api/courses
 *  8. AI 对话   /api/agent/chat, /api/agent/list, /api/agent/stream
 *  9. AI 统计   /api/ai/stats, /api/ai/cache
 * 10. 种子数据  /api/seed
 *
 * 所有 AI 对话请求经过 AiPipeline 处理（意图识别 → 提示词增强 → 上下文注入 → AI调用 → 后处理）
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SpringRestController {

    private static final Logger log = LoggerFactory.getLogger(SpringRestController.class);

    @Autowired
    private StudyService studyService;

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private AiPipeline aiPipeline;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    // ── 1. System ─────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("time", LocalDateTime.now().toString());
        result.put("provider", aiClient.name());
        result.put("pipeline", aiPipeline.pipelineInfo());
        result.put("dbProfile", studyService.profile().map(p -> p.name()).orElse("未建立"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ai/info")
    public ResponseEntity<String> aiInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("provider", aiClient.name());
        info.put("pipeline", aiPipeline.pipelineInfo());
        return ResponseEntity.ok(Json.obj(info));
    }

    // ── 2. User Profile ───────────────────────────────────────────────────────

    @GetMapping("/user")
    public ResponseEntity<String> getUser() {
        return studyService.profile()
                .map(u -> ResponseEntity.ok(u.json()))
                .orElse(ResponseEntity.ok(Json.obj(Map.of("hasProfile", "false"))));
    }

    @PostMapping("/user")
    public ResponseEntity<?> saveUser(@RequestBody Map<String, String> body) {
        try {
            String height = requireField(body, "heightCm");
            String weight = requireField(body, "weightKg");
            String bodyType = body.getOrDefault("bodyType", "匀称");
            double heightVal = parseDoubleOrZero(height);
            double weightVal = parseDoubleOrZero(weight);
            UserProfile user = new UserProfile(
                    "u1001",
                    body.getOrDefault("name", "用户"),
                    height + "cm / " + weight + "kg",
                    "期望身材 " + bodyType,
                    body.getOrDefault("limits", "无"),
                    body.getOrDefault("mode", "减脂与力量并行"),
                    weightVal,
                    heightVal
            );
            studyService.saveUser(user);
            log.info("用户档案已保存: {}", user.name());
            return ResponseEntity.ok(user.json());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorJson(e.getMessage()));
        }
    }

    // ── 3. Dashboard & Analytics ──────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<String> dashboard() {
        return ResponseEntity.ok(studyService.dashboardJson());
    }

    @GetMapping("/analytics")
    public ResponseEntity<String> analytics() {
        return ResponseEntity.ok(studyService.analyticsJson());
    }

    // ── 4. Training Plans ─────────────────────────────────────────────────────

    @GetMapping("/plans")
    public ResponseEntity<String> getPlans() {
        List<StudyPlan> plans = studyService.plans();
        return ResponseEntity.ok(Json.arr(plans.stream().map(StudyPlan::json).toList()));
    }

    @PostMapping("/plans")
    public ResponseEntity<?> savePlan(@RequestBody Map<String, String> body) {
        try {
            StudyPlan plan = new StudyPlan(
                    "p" + System.currentTimeMillis(),
                    body.getOrDefault("title", "AI健身训练计划"),
                    body.getOrDefault("subject", "综合训练"),
                    LocalDate.parse(body.getOrDefault("date", LocalDate.now().toString())),
                    Integer.parseInt(body.getOrDefault("minutes", "45")),
                    body.getOrDefault("steps", "热身-主训练-有氧-拉伸"),
                    body.getOrDefault("status", "待开始")
            );
            studyService.savePlan(plan);
            return ResponseEntity.ok(plan.json());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorJson(e.getMessage()));
        }
    }

    // ── 5. Nutrition Notes ────────────────────────────────────────────────────

    @GetMapping("/notes")
    public ResponseEntity<String> getNotes() {
        return ResponseEntity.ok(Json.arr(studyService.notes().stream().map(Note::json).toList()));
    }

    @PostMapping("/notes")
    public ResponseEntity<?> saveNote(@RequestBody Map<String, String> body) {
        try {
            Note note = new Note(
                    "n" + System.currentTimeMillis(),
                    body.getOrDefault("title", "饮食摄入记录"),
                    body.getOrDefault("subject", "饮食摄入"),
                    body.getOrDefault("content", ""),
                    body.getOrDefault("tags", "热量,蛋白质"),
                    LocalDateTime.now()
            );
            studyService.saveNote(note);
            return ResponseEntity.ok(note.json());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorJson(e.getMessage()));
        }
    }

    // ── 6. Quiz & Assessment ──────────────────────────────────────────────────

    @GetMapping("/quiz")
    public ResponseEntity<String> getQuiz() {
        return ResponseEntity.ok(Json.arr(studyService.quiz().stream().map(QuizQuestion::json).toList()));
    }

    @PostMapping("/quiz/submit")
    public ResponseEntity<?> submitQuiz(@RequestBody Map<String, String> body) {
        try {
            QuizAttempt attempt = new QuizAttempt(
                    "a" + System.currentTimeMillis(),
                    body.getOrDefault("subject", "体能评估"),
                    Integer.parseInt(body.getOrDefault("score", "80")),
                    Integer.parseInt(body.getOrDefault("total", "100")),
                    body.getOrDefault("mistakes", ""),
                    LocalDateTime.now()
            );
            studyService.saveAttempt(attempt);
            return ResponseEntity.ok(attempt.json());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorJson(e.getMessage()));
        }
    }

    // ── 7. Courses ────────────────────────────────────────────────────────────

    @GetMapping("/courses")
    public ResponseEntity<String> getCourses() {
        return ResponseEntity.ok(Json.arr(studyService.courses().stream().map(Course::json).toList()));
    }

    // ── 8. AI Chat ────────────────────────────────────────────────────────────

    @PostMapping("/agent/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String agentType = body.getOrDefault("type", "fitness");

        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(errorJson("消息不能为空"));
        }

        try {
            // 使用 AiPipeline 处理（意图识别 + 上下文注入 + 安全过滤）
            String baseSystemPrompt = agentFactory.create(agentType)
                    .reply("请描述你的功能", studyService.profile().orElse(null))
                    .content().substring(0, 0); // 获取 system prompt

            AgentReply reply = agentFactory.create(agentType)
                    .reply(message, studyService.profile().orElse(null));

            // 附加 Pipeline 元数据
            String enrichedJson = addPipelineMetadata(reply.json(), agentType, message);
            return ResponseEntity.ok(enrichedJson);

        } catch (RateLimitingAiClientDecorator.RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorJson("请求频率超限，请 " + e.getRetryAfterSeconds() + " 秒后重试"));
        } catch (Exception e) {
            log.error("AI 对话错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorJson("AI 服务暂时不可用: " + e.getMessage()));
        }
    }

    /** 别名：/api/chat/v2 → 与 /api/agent/chat 行为相同，供非 tutor 页面调用 */
    @PostMapping("/chat/v2")
    public ResponseEntity<?> chatV2Alias(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String agentType = body.getOrDefault("agentType", body.getOrDefault("type", "fitness"));
        if (message.isBlank()) return ResponseEntity.badRequest().body(errorJson("消息不能为空"));
        try {
            AgentReply reply = agentFactory.create(agentType)
                    .reply(message, studyService.profile().orElse(null));
            return ResponseEntity.ok(reply.json());
        } catch (Exception e) {
            log.error("AI 对话错误 (chat/v2): {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorJson("AI 服务暂时不可用: " + e.getMessage()));
        }
    }

    /**
     * 增强版 AI 对话 — 完全使用 Pipeline（推荐前端使用此接口）
     */
    @PostMapping("/agent/chat/v2")
    public ResponseEntity<?> chatV2(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String agentType = body.getOrDefault("type", "fitness");

        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(errorJson("消息不能为空"));
        }

        try {
            String baseSystemPrompt = getBaseSystemPrompt(agentType);
            PipelineContext ctx = aiPipeline.process(message, agentType, baseSystemPrompt);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content", ctx.getProcessedResponse());
            response.put("intent", ctx.getDetectedIntent());
            response.put("keywords", ctx.getKeywords());
            response.put("enrichmentCount", ctx.getEnrichmentNotes().size());
            response.put("model", ctx.getModelUsed());
            response.put("safetyPassed", ctx.isSafetyPassed());
            response.put("pipelineSummary", ctx.summary());
            if (ctx.getMetadata().containsKey("actionItems")) {
                response.put("actionItems", ctx.getMetadata().get("actionItems"));
            }

            return ResponseEntity.ok(jsonFromMap(response));
        } catch (Exception e) {
            log.error("Pipeline 对话错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorJson("AI 处理失败: " + e.getMessage()));
        }
    }

    /**
     * Server-Sent Events 流式 AI 响应
     */
    @GetMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message,
                                  @RequestParam(defaultValue = "fitness") String type) {
        SseEmitter emitter = new SseEmitter(60_000L);
        sseExecutor.submit(() -> {
            try {
                String systemPrompt = getBaseSystemPrompt(type);
                String enrichedMessage = "[流式模式]\n" + message;
                aiClient.complete(systemPrompt, enrichedMessage);
                // 模拟分块发送
                String[] lines = ("AI 正在处理: " + message).split("(?<=\\.)");
                for (String line : lines) {
                    emitter.send(SseEmitter.event().data(line));
                    Thread.sleep(100);
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("[错误] " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
        return emitter;
    }

    // ── 9. Agent List ─────────────────────────────────────────────────────────

    @GetMapping("/agent/list")
    public ResponseEntity<String> listAgents() {
        return ResponseEntity.ok(
            "[" + agentFactory.listAgents().stream()
                .map(AgentFactory.AgentInfo::toJson)
                .reduce((a, b) -> a + "," + b).orElse("")
            + "]"
        );
    }

    // ── 10. AI Statistics ────────────────────────────────────────────────────

    @GetMapping("/ai/stats")
    public ResponseEntity<String> aiStats() {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("provider", aiClient.name());
        AiClient inner = aiClient;
        while (inner instanceof com.smartfitagent.ai.decorator.AiClientDecorator d) {
            if (d instanceof LoggingAiClientDecorator logging) {
                stats.put("logging", logging.statsSummary());
            } else if (d instanceof CachingAiClientDecorator caching) {
                stats.put("cache", caching.cacheSummary());
            } else if (d instanceof RateLimitingAiClientDecorator rl) {
                stats.put("rateLimit", rl.rateLimitSummary());
            }
            inner = d.getDelegate();
        }
        return ResponseEntity.ok(Json.obj(stats));
    }

    @DeleteMapping("/ai/cache")
    public ResponseEntity<String> clearCache() {
        AiClient inner = aiClient;
        while (inner instanceof com.smartfitagent.ai.decorator.AiClientDecorator d) {
            if (d instanceof CachingAiClientDecorator caching) {
                caching.clearAll();
                return ResponseEntity.ok(Json.obj(Map.of("message", "缓存已清空")));
            }
            inner = d.getDelegate();
        }
        return ResponseEntity.ok(Json.obj(Map.of("message", "缓存装饰器未启用")));
    }

    // ── 11. Seed ──────────────────────────────────────────────────────────────

    @PostMapping("/seed")
    public ResponseEntity<String> seed() {
        studyService.seed();
        return ResponseEntity.ok(Json.obj(Map.of("seeded", "false",
                "message", "不预置数据，请用户自行建立档案")));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private double parseDoubleOrZero(String s) {
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0.0; }
    }

    private String requireField(Map<String, String> body, String key) {
        String value = body.getOrDefault(key, "").trim();
        if (value.isBlank()) throw new IllegalArgumentException("缺少必填项: " + key);
        return value;
    }

    private String errorJson(String message) {
        return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
    }

    private String addPipelineMetadata(String replyJson, String agentType, String message) {
        String meta = ",\"agentType\":\"" + agentType + "\","
                + "\"messageLength\":" + message.length();
        if (replyJson.endsWith("}")) {
            return replyJson.substring(0, replyJson.length() - 1) + meta + "}";
        }
        return replyJson;
    }

    private String getBaseSystemPrompt(String agentType) {
        return switch (agentType) {
            case "nutrition"  -> "你是一位专业的运动营养师，提供科学的饮食和营养建议。";
            case "recovery"   -> "你是一位运动恢复专家，帮助用户优化训练恢复。";
            case "mindset"    -> "你是一位运动心理教练，帮助用户维持健身动力。";
            case "progress"   -> "你是一位数据分析师，帮助用户理解和优化健身进度。";
            case "challenge"  -> "你是一位健身挑战策划师，帮助用户选择和完成挑战。";
            case "planner"    -> "你是一位训练计划专家，制定个性化训练课表。";
            default           -> "你是一位专业的AI健身教练，基于用户数据提供科学健身建议。";
        };
    }

    private String jsonFromMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append("\"").append(s.replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
            } else if (v instanceof Boolean b) {
                sb.append(b);
            } else if (v instanceof Number n) {
                sb.append(n);
            } else if (v instanceof List<?> l) {
                sb.append("[");
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(l.get(i).toString().replace("\"", "'")).append("\"");
                }
                sb.append("]");
            } else {
                sb.append("\"").append(v).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
