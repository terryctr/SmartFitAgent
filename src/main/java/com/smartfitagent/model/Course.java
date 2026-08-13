package com.smartfitagent.model;
import com.smartfitagent.Json; import java.util.Map;
public record Course(String id,String title,String subject,String level,String description,int lessons,String teacher,int progress){ public String json(){return Json.obj(Map.of("id",id,"title",title,"subject",subject,"level",level,"description",description,"lessons",String.valueOf(lessons),"teacher",teacher,"progress",String.valueOf(progress)));} }
