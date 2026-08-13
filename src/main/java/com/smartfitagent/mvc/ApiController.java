package com.smartfitagent.mvc;

import com.smartfitagent.Json;
import com.smartfitagent.StudyService;
import com.smartfitagent.agent.AgentFactory;
import com.smartfitagent.model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public class ApiController {
    private final StudyService service;
    private final AgentFactory agents;

    public ApiController(StudyService service, AgentFactory agents) {
        this.service = service;
        this.agents = agents;
    }

    public Response health(Request r) {
        return Response.json(Json.obj(Map.of("status", "UP", "time", LocalDateTime.now().toString())));
    }

    public Response dashboard(Request r) { return Response.json(service.dashboardJson()); }
    public Response analytics(Request r) { return Response.json(service.analyticsJson()); }
    public Response courses(Request r) { return Response.json(Json.arr(service.courses().stream().map(Course::json).toList())); }
    public Response plans(Request r) { return Response.json(Json.arr(service.plans().stream().map(StudyPlan::json).toList())); }
    public Response notes(Request r) { return Response.json(Json.arr(service.notes().stream().map(Note::json).toList())); }
    public Response quiz(Request r) { return Response.json(Json.arr(service.quiz().stream().map(QuizQuestion::json).toList())); }

    public Response seed(Request r) {
        service.seed();
        return Response.json(Json.obj(Map.of("seeded", "false", "message", "不预置数据，请用户自行建立档案")));
    }

    public Response saveUser(Request r) throws Exception {
        Map<String, String> body = r.json();
        String height = required(body, "heightCm");
        String weight = required(body, "weightKg");
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
        service.saveUser(user);
        return Response.json(user.json());
    }

    public Response savePlan(Request r) throws Exception {
        Map<String, String> body = r.json();
        StudyPlan plan = new StudyPlan(
                "p" + System.currentTimeMillis(),
                body.getOrDefault("title", "AI健身训练计划"),
                body.getOrDefault("subject", "综合训练"),
                LocalDate.parse(body.getOrDefault("date", LocalDate.now().toString())),
                Integer.parseInt(body.getOrDefault("minutes", "45")),
                body.getOrDefault("steps", "热身-主训练-有氧-拉伸"),
                body.getOrDefault("status", "待开始")
        );
        service.savePlan(plan);
        return Response.json(plan.json());
    }

    public Response saveNote(Request r) throws Exception {
        Map<String, String> body = r.json();
        Note note = new Note(
                "n" + System.currentTimeMillis(),
                body.getOrDefault("title", "饮食摄入记录"),
                body.getOrDefault("subject", "饮食摄入"),
                body.getOrDefault("content", ""),
                body.getOrDefault("tags", "热量,蛋白质"),
                LocalDateTime.now()
        );
        service.saveNote(note);
        return Response.json(note.json());
    }

    public Response saveAttempt(Request r) throws Exception {
        Map<String, String> body = r.json();
        QuizAttempt attempt = new QuizAttempt(
                "a" + System.currentTimeMillis(),
                body.getOrDefault("subject", "体能评估"),
                Integer.parseInt(body.getOrDefault("score", "80")),
                Integer.parseInt(body.getOrDefault("total", "100")),
                body.getOrDefault("mistakes", "训练执行和饮食稳定性需要继续优化"),
                LocalDateTime.now()
        );
        service.saveAttempt(attempt);
        return Response.json(attempt.json());
    }

    public Response chat(Request r) throws Exception {
        Map<String, String> body = r.json();
        AgentReply reply = agents.create(body.getOrDefault("type", "fitness"))
                .reply(body.getOrDefault("message", "请根据我的BMI制定减脂和增肌计划"), service.user());
        return Response.json(reply.json());
    }

    private double parseDoubleOrZero(String s) {
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0.0; }
    }

    private String required(Map<String, String> body, String key) {
        String value = body.getOrDefault(key, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("缺少必填项: " + key);
        }
        return value;
    }
}
