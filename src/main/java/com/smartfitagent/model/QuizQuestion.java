package com.smartfitagent.model;
import com.smartfitagent.Json; import java.util.Map;
public record QuizQuestion(String id,String subject,String stem,String optionA,String optionB,String optionC,String optionD,String answer,String explanation){ public String json(){return Json.obj(Map.of("id",id,"subject",subject,"stem",stem,"optionA",optionA,"optionB",optionB,"optionC",optionC,"optionD",optionD,"answer",answer,"explanation",explanation));} }
