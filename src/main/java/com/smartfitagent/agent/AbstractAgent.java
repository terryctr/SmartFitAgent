package com.smartfitagent.agent;

import com.smartfitagent.ai.AiClient;
import com.smartfitagent.model.AgentReply;
import com.smartfitagent.model.UserProfile;

public abstract class AbstractAgent implements Agent {
    protected final AiClient ai;

    protected AbstractAgent(AiClient ai) {
        this.ai = ai;
    }

    public AgentReply reply(String message, UserProfile user) throws Exception {
        String system = systemPrompt(user);
        String answer = ai.complete(system, "用户问题：" + message + "\n请输出分析、训练步骤、饮食建议、风险提醒和下一步行动。");
        return new AgentReply(type(), answer, nextActions(), usedContext(), ai.name());
    }

    protected abstract String type();
    protected abstract String systemPrompt(UserProfile user);
    protected abstract String nextActions();
    protected abstract String usedContext();
}
