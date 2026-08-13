package com.smartfitagent;

import com.smartfitagent.db.Database;
import com.smartfitagent.event.LearningEvent;
import com.smartfitagent.event.LearningEventBus;
import com.smartfitagent.model.*;
import java.util.*;

public class StudyService {
    private final Database db;
    private final LearningEventBus bus;
    private int activityEvents = 0;

    public StudyService(Database db, LearningEventBus bus) {
        this.db = db;
        this.bus = bus;
        this.bus.register(e -> activityEvents++);
    }

    public Optional<UserProfile> profile() {
        return db.user();
    }

    public UserProfile user() {
        return profile().orElseThrow(() -> new IllegalStateException("请先建立个人健身档案"));
    }

    public void saveUser(UserProfile user) {
        db.saveUser(user);
        bus.publish(new LearningEvent("PROFILE_CREATED", user.name()));
    }

    public List<Course> courses() { return db.courses(); }
    public List<StudyPlan> plans() { return db.plans(); }
    public List<Note> notes() { return db.notes(); }
    public List<QuizQuestion> quiz() { return db.questions(); }
    public List<QuizAttempt> attempts() { return db.attempts(); }
    public void savePlan(StudyPlan plan) { db.savePlan(plan); bus.publish(new LearningEvent("TRAINING_PLAN", plan.title())); }
    public void saveNote(Note note) { db.saveNote(note); bus.publish(new LearningEvent("NUTRITION", note.title())); }
    public void saveAttempt(QuizAttempt attempt) { db.saveAttempt(attempt); bus.publish(new LearningEvent("FITNESS_ASSESSMENT", attempt.subject() + attempt.score())); }

    public String promptContext() {
        if (profile().isEmpty()) {
            return "用户尚未建立档案。必须先要求用户填写身高、体重、期望身材和训练模式。";
        }
        UserProfile user = user();
        return "身体档案=" + user
                + "\n现场计算=" + dashboardJson()
                + "\n用户记录的训练计划=" + plans()
                + "\n用户记录的饮食摄入=" + notes()
                + "\n用户记录的体能评估=" + attempts()
                + "\n请只依据用户填写和记录的数据制定方案，不要编造身高、体重、训练史或饮食记录。";
    }

    public String dashboardJson() {
        Optional<UserProfile> optional = profile();
        if (optional.isEmpty()) {
            return Json.obj(Map.of(
                    "hasProfile", "false",
                    "recommendations", Json.arr(List.of()),
                    "fatLossPlan", Json.arr(List.of()),
                    "strengthPlan", Json.arr(List.of())
            ));
        }

        UserProfile user = optional.get();
        double height = heightCm(user);
        double weight = weightKg(user);
        double currentBmi = bmi(height, weight);
        String bodyType = expectedBody(user);
        double targetBmi = targetBmi(bodyType);
        double targetWeight = targetBmi * Math.pow(height / 100.0, 2);
        String mode = user.preferredStyle();
        int fatLossSessions = fatLossSessions(mode, currentBmi, targetBmi);
        int strengthSessions = strengthSessions(mode, bodyType);
        int weeklyMinutes = fatLossSessions * 40 + strengthSessions * 55;
        int calorieTarget = calorieTarget(weight, currentBmi, targetBmi, bodyType);
        int protein = proteinTarget(weight, bodyType);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("hasProfile", "true");
        data.put("user", user.json());
        data.put("heightCm", one(height));
        data.put("weightKg", one(weight));
        data.put("expectedBody", bodyType);
        data.put("currentBMI", one(currentBmi));
        data.put("targetBMI", one(targetBmi));
        data.put("targetWeightKg", one(targetWeight));
        data.put("weightDeltaKg", one(weight - targetWeight));
        data.put("weeklyTrainingMinutes", String.valueOf(weeklyMinutes));
        data.put("strengthSessions", String.valueOf(strengthSessions));
        data.put("fatLossSessions", String.valueOf(fatLossSessions));
        data.put("calorieTarget", String.valueOf(calorieTarget));
        data.put("proteinTarget", String.valueOf(protein));
        data.put("planCount", String.valueOf(plans().size()));
        data.put("noteCount", String.valueOf(notes().size()));
        data.put("assessmentCount", String.valueOf(attempts().size()));
        data.put("activityEvents", String.valueOf(activityEvents));
        data.put("mode", mode);
        data.put("recommendations", recommendations(currentBmi, targetBmi, bodyType, calorieTarget, protein, fatLossSessions, strengthSessions));
        data.put("fatLossPlan", fatLossPlan(fatLossSessions));
        data.put("strengthPlan", strengthPlan(strengthSessions, bodyType));
        return Json.obj(data);
    }

    public String analyticsJson() {
        if (profile().isEmpty()) {
            return Json.obj(Map.of(
                    "hasProfile", "false",
                    "summary", "请先建立个人健身档案，系统才会现场计算 BMI 并生成训练与饮食方案。",
                    "radar", Json.arr(List.of())
            ));
        }
        return Json.obj(Map.of(
                "hasProfile", "true",
                "summary", "方案依据用户填写的身高、体重、期望身材和训练模式现场生成；后续会随着训练计划、饮食记录和体能评估不断调整。",
                "radar", Json.arr(List.of("70", "65", "72", "60", "68", "66")),
                "dashboard", dashboardJson()
        ));
    }

    public void seed() {
        // 不预置任何用户身体数据或训练记录。用户必须先自行建立档案。
    }

    private String recommendations(double currentBmi, double targetBmi, String bodyType, int calories, int protein, int fatLossSessions, int strengthSessions) {
        List<String> rows = new ArrayList<>();
        rows.add(Json.obj(Map.of("title", "BMI分析", "reason", "当前BMI " + one(currentBmi) + "，目标BMI " + one(targetBmi) + "，期望身材为" + bodyType + "。")));
        rows.add(Json.obj(Map.of("title", "训练量", "reason", "建议每周减脂训练" + fatLossSessions + "次、力量训练" + strengthSessions + "次，可按用户选择并行或分开执行。")));
        rows.add(Json.obj(Map.of("title", "饮食摄入", "reason", "每日热量约" + calories + "kcal，蛋白质约" + protein + "g，后续根据体重变化微调。")));
        return Json.arr(rows);
    }

    private String fatLossPlan(int sessions) {
        if (sessions == 0) return Json.arr(List.of(Json.obj(Map.of("title", "本阶段不安排减脂训练", "detail", "用户当前选择以力量训练为主。"))));
        return Json.arr(List.of(
                Json.obj(Map.of("title", "Zone2有氧", "detail", "每周" + Math.max(1, sessions - 1) + "次，每次35-45分钟，心率保持在最大心率60%-70%。")),
                Json.obj(Map.of("title", "间歇训练", "detail", "每周最多1次，8-10轮高低强度交替，避免和腿部大重量同日。")),
                Json.obj(Map.of("title", "日常活动", "detail", "日均8000-10000步，用低压力方式增加能量消耗。"))
        ));
    }

    private String strengthPlan(int sessions, String bodyType) {
        if (sessions == 0) return Json.arr(List.of(Json.obj(Map.of("title", "本阶段不安排力量训练", "detail", "用户当前选择以减脂训练为主。"))));
        String volume = "强壮".equals(bodyType) ? "每个大肌群每周12-16组" : "每个大肌群每周8-12组";
        return Json.arr(List.of(
                Json.obj(Map.of("title", "复合动作", "detail", "深蹲、卧推、划船、硬拉或髋铰链动作优先，动作质量高于重量。")),
                Json.obj(Map.of("title", "训练频率", "detail", "每周" + sessions + "次力量训练，采用全身或上/下肢拆分。")),
                Json.obj(Map.of("title", "增肌容量", "detail", volume + "，每周小幅增加重量、次数或组数。"))
        ));
    }

    private double heightCm(UserProfile user) {
        return extractNumber(user.grade(), 0.0);
    }

    private double weightKg(UserProfile user) {
        String text = user.grade();
        int kg = text.indexOf("kg");
        if (kg > 0) {
            int slash = text.lastIndexOf('/', kg);
            int start = slash >= 0 ? slash + 1 : 0;
            return extractNumber(text.substring(start, kg), 0.0);
        }
        return 0.0;
    }

    private String expectedBody(UserProfile user) {
        String goal = user.goal();
        if (goal.contains("偏瘦")) return "偏瘦";
        if (goal.contains("强壮")) return "强壮";
        return "匀称";
    }

    private double targetBmi(String bodyType) {
        return switch (bodyType) {
            case "偏瘦" -> 20.0;
            case "强壮" -> 24.0;
            default -> 22.0;
        };
    }

    private double bmi(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) return 0.0;
        double meters = heightCm / 100.0;
        return weightKg / (meters * meters);
    }

    private int fatLossSessions(String mode, double currentBmi, double targetBmi) {
        if (mode.contains("只做力量")) return 0;
        if (mode.contains("只做减脂")) return 4;
        return currentBmi > targetBmi + 1.0 ? 3 : 2;
    }

    private int strengthSessions(String mode, String bodyType) {
        if (mode.contains("只做减脂")) return 0;
        if (mode.contains("只做力量")) return 4;
        return "强壮".equals(bodyType) ? 4 : 3;
    }

    private int calorieTarget(double weightKg, double currentBmi, double targetBmi, String bodyType) {
        int maintenance = (int) Math.round(weightKg * 30);
        if ("强壮".equals(bodyType) && currentBmi <= targetBmi) return maintenance + 200;
        if (currentBmi > targetBmi + 0.8) return maintenance - 350;
        if (currentBmi < targetBmi - 0.8) return maintenance + 150;
        return maintenance;
    }

    private int proteinTarget(double weightKg, String bodyType) {
        double factor = "强壮".equals(bodyType) ? 2.0 : 1.7;
        return (int) Math.round(weightKg * factor);
    }

    private double extractNumber(String text, double fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+(\\.\\d+)?").matcher(text == null ? "" : text);
        return matcher.find() ? Double.parseDouble(matcher.group()) : fallback;
    }

    private String one(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }
}
