package com.smartfitagent.model;
import com.smartfitagent.Json; import java.time.LocalDate; import java.util.Map;
public record StudyPlan(String id,String title,String subject,LocalDate date,int minutes,String steps,String status){ public String json(){return Json.obj(Map.of("id",id,"title",title,"subject",subject,"date",date.toString(),"minutes",String.valueOf(minutes),"steps",steps,"status",status));} }
