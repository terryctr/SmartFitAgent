package com.smartfitagent.model;
import com.smartfitagent.Json; import java.time.LocalDateTime; import java.util.Map;
public record QuizAttempt(String id,String subject,int score,int total,String mistakes,LocalDateTime createdAt){ public String json(){return Json.obj(Map.of("id",id,"subject",subject,"score",String.valueOf(score),"total",String.valueOf(total),"mistakes",mistakes,"createdAt",createdAt.toString()));} }
