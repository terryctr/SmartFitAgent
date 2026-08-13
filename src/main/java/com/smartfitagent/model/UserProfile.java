package com.smartfitagent.model;
import com.smartfitagent.Json; import java.util.Map;
public record UserProfile(String id,String name,String grade,String goal,String weakness,String preferredStyle,double weightKg,double heightCm){ public String json(){return Json.obj(Map.of("id",id,"name",name,"grade",grade,"goal",goal,"weakness",weakness,"preferredStyle",preferredStyle,"weightKg",String.valueOf(weightKg),"heightCm",String.valueOf(heightCm)));} }
