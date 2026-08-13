package com.smartfitagent.model;
import com.smartfitagent.Json; import java.time.LocalDateTime; import java.util.Map;
public record Note(String id,String title,String subject,String content,String tags,LocalDateTime createdAt){ public String json(){return Json.obj(Map.of("id",id,"title",title,"subject",subject,"content",content,"tags",tags,"createdAt",createdAt.toString()));} }
