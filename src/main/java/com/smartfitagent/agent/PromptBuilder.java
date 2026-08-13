package com.smartfitagent.agent;

import com.smartfitagent.model.UserProfile;

public class PromptBuilder {
    private final StringBuilder sb = new StringBuilder();

    public PromptBuilder identity(String value) {
        sb.append("你是").append(value).append("。\n");
        return this;
    }

    public PromptBuilder user(UserProfile user) {
        if (user == null) {
            sb.append("用户=未设置档案（请在个人档案页填写信息以获得个性化建议）\n");
            return this;
        }
        sb.append("用户=").append(user.name())
                .append(" 身高体重=").append(user.grade())
                .append(" 目标=").append(user.goal())
                .append(" 身体限制=").append(user.weakness())
                .append(" 训练模式=").append(user.preferredStyle())
                .append("\n");
        return this;
    }

    public PromptBuilder context(String name, String value) {
        sb.append("【").append(name).append("】").append(value).append("\n");
        return this;
    }

    public PromptBuilder rule(String rule) {
        sb.append("规则：").append(rule).append("\n");
        return this;
    }

    public String build() { return sb.toString(); }
}
