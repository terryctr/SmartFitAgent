package com.smartfitagent.model;
import com.smartfitagent.Json; import java.util.Map;
public record AgentReply(String agentType,String answer,String nextActions,String usedContext,String provider){
    public String content(){ return answer; }
    public String json(){return Json.obj(Map.of("agentType",agentType,"answer",answer,"nextActions",nextActions,"usedContext",usedContext,"provider",provider));}
}
