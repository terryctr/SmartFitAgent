package com.smartfitagent.ai;

public class MockAiClient implements AiClient {
    public String name() { return "MockAI"; }

    public String complete(String systemPrompt, String userPrompt) {
        return "我会结合你的身高、体重、当前BMI、目标BMI、训练偏好和饮食记录来生成计划。\n"
                + "1. 减脂：每周3次有氧，每次35-45分钟，日均步数8000-10000。\n"
                + "2. 力量：每周3-4次复合动作训练，每个大肌群每周10-14组。\n"
                + "3. 饮食：保持轻度热量缺口，蛋白质按1.6-2.0g/kg体重摄入。\n"
                + "4. 恢复：每晚7小时以上睡眠，训练后做5-10分钟拉伸。\n\n"
                + "你的问题：" + userPrompt + "\n上下文摘要："
                + systemPrompt.substring(0, Math.min(220, systemPrompt.length()));
    }
}
