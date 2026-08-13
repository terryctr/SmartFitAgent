package com.smartfitagent.agent;

import com.smartfitagent.StudyService;
import com.smartfitagent.ai.AiClient;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Agent 工厂类（工厂方法模式，Factory Method Pattern）
 *
 * 职责：根据 type 字符串创建对应的 Agent 实例。
 * 调用方无需知道具体 Agent 的构造方式，只需传入类型字符串。
 *
 * 支持的 Agent 类型：
 *  - fitness    : 综合健身教练（默认）
 *  - planner    : 训练计划制定专家
 *  - nutrition  : 营养饮食专家（新增）
 *  - recovery   : 恢复休息专家（新增）
 *  - health     : 综合健康顾问
 *  - writing    : 健身日志撰写助手
 *  - mindset    : 心态与动力教练（新增）
 *  - progress   : 进度分析专家（新增）
 *  - challenge  : 挑战赛策划师（新增）
 *
 * 扩展说明：
 *  添加新 Agent 只需：
 *  1. 创建继承 AbstractAgent 的新类
 *  2. 在 create() 的 switch 中添加一行映射
 *  无需修改任何调用方代码（开闭原则）。
 */
@Component
public class AgentFactory {

    private final AiClient ai;
    private final StudyService service;

    public AgentFactory(AiClient ai, StudyService service) {
        this.ai = ai;
        this.service = service;
    }

    /**
     * 根据类型创建 Agent 实例
     * @param type Agent 类型（不区分大小写，null 时使用 fitness）
     * @return 对应的 Agent 实例
     */
    public Agent create(String type) {
        return switch (type == null ? "fitness" : type.toLowerCase().trim()) {
            case "planner"    -> new PlannerAgent(ai, service);
            case "nutrition"  -> new NutritionAgent(ai, service);
            case "recovery"   -> new RecoveryAgent(ai, service);
            case "health"     -> new HealthAgent(ai, service);
            case "writing"    -> new WritingAgent(ai, service);
            case "mindset"    -> new MindsetAgent(ai, service);
            case "progress"   -> new ProgressAgent(ai, service);
            case "challenge"  -> new ChallengeAgent(ai, service);
            default           -> new TutorAgent(ai, service);  // fitness / 未知类型
        };
    }

    /**
     * 返回所有支持的 Agent 类型列表
     * 用于前端 Agent 选择器
     */
    public List<AgentInfo> listAgents() {
        return List.of(
            new AgentInfo("fitness",   "AI健身教练",     "综合健身指导，BMI分析，训练+饮食双线建议",   "🏋️"),
            new AgentInfo("planner",   "训练计划师",     "制定个性化周训练课表，渐进超负荷规划",       "📅"),
            new AgentInfo("nutrition", "营养饮食师",     "宏营养素计算，增肌/减脂饮食方案",             "🥗"),
            new AgentInfo("recovery",  "恢复专家",       "DOMS管理，睡眠优化，主动恢复策略",           "😴"),
            new AgentInfo("health",    "健康顾问",       "综合健康评估，生活方式改善建议",               "❤️"),
            new AgentInfo("mindset",   "心态教练",       "克服平台期，维持动力，建立健康习惯",           "🧠"),
            new AgentInfo("progress",  "进度分析师",     "数据驱动的进步分析，达标时间预测",             "📊"),
            new AgentInfo("challenge", "挑战策划师",     "个性化挑战推荐，游戏化健身激励",               "🏆"),
            new AgentInfo("writing",   "日志撰写助手",   "整理训练日志，撰写健身计划文档",               "✍️")
        );
    }

    /**
     * Agent 元信息 DTO
     */
    public record AgentInfo(String type, String name, String description, String emoji) {
        public String toJson() {
            return String.format("{\"type\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"emoji\":\"%s\"}",
                    type, name, description, emoji);
        }
    }
}
