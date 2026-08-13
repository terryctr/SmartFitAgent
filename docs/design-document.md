# SmartFitAgent — 软件设计文档

## 1. 系统架构概述

SmartFitAgent 采用标准 MVC 分层架构，以 Spring Boot 3.2 为 Web 框架，前端使用原生 HTML5 + CSS3 + JavaScript，后端通过 REST API 与前端通信，数据持久化支持 H2（开发）和 MySQL（生产）双模式切换。

### 1.1 系统架构图（文字描述）

```
┌─────────────────────────────────────────────────────────────┐
│                        前端层（Browser）                       │
│  HTML5 + CSS3 + Vanilla JS（20 个页面/功能模块）               │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐             │
│  │仪表盘│ │AI教练│ │训练  │ │营养  │ │成就  │  ...更多     │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘             │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP REST / SSE
┌────────────────────────▼────────────────────────────────────┐
│                   控制器层（Controller Layer）                  │
│  SpringRestController  WorkoutController  NutritionController │
│  ChallengeController   MeasurementController  SleepController │
│  GlobalExceptionHandler（@RestControllerAdvice）              │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                   业务逻辑层（Service Layer）                   │
│  StudyService  WorkoutLogService  NutritionLogService         │
│  AchievementService  ChallengeService                         │
│  BodyMeasurementService  SleepLogService                      │
└──────────┬─────────────────────────┬───────────────────────┘
           │                         │
┌──────────▼──────────┐  ┌───────────▼─────────────────────┐
│   数据访问层（DB）    │  │        AI 模块层                 │
│  JdbcTemplate       │  │  AiPipeline（5步处理链）          │
│  MySQL / H2         │  │  AgentFactory（9种Agent）         │
└─────────────────────┘  │  LlmStrategy（Qwen/DeepSeek/Kimi）│
                         │  装饰器链（Log→Cache→RateLimit）  │
                         └─────────────────────────────────┘
```

### 1.2 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | HTML5, CSS3 (CSS Variables, Grid, Flexbox), Vanilla JavaScript (ES2022) |
| Web框架 | Spring Boot 3.2.5, Spring Web MVC |
| AI集成 | Qwen (DashScope), DeepSeek, Kimi (Moonshot) |
| 流式输出 | Server-Sent Events (SSE) |
| 数据库 | MySQL 8.0 / H2 2.2（JdbcTemplate，无 ORM） |
| 缓存 | LRU LinkedHashMap（应用内缓存）+ SHA-256 键哈希 |
| 限流 | 滑动窗口（Deque<Long>），每用户独立计数 |
| 构建 | Maven 3.9, Spring Boot Maven Plugin |
| 日志 | SLF4J + Logback |

---

## 2. 模块划分与类职责

### 2.1 Spring 配置模块

| 类 | 职责 |
|----|------|
| `spring.SpringApp` | `@SpringBootApplication` 入口，启用缓存/异步/调度 |
| `spring.AppConfig` | `@Configuration`：组装 Database Bean、AiClient 装饰器链、线程池 |
| `spring.AiProperties` | `@ConfigurationProperties("ai")`：映射 application.yml AI 配置 |

### 2.2 控制器层

| 类 | 职责 |
|----|------|
| `controller.SpringRestController` | 核心 AI 对话（含 SSE 流式）、仪表盘、Agent API、AI 统计 |
| `controller.WorkoutController` | 训练日志 CRUD、统计、AI 推荐 |
| `controller.NutritionController` | 饮食记录、每日汇总、宏量素、食物数据库 |
| `controller.ChallengeAchievementController` | 挑战列表/参与/打卡、成就查询/检查 |
| `controller.MeasurementController` | 身体测量 CRUD、趋势分析、WHR 分析 |
| `controller.SleepController` | 睡眠记录、周统计、睡眠改善提示 |
| `controller.GlobalExceptionHandler` | `@RestControllerAdvice`：统一 JSON 错误响应 |

### 2.3 业务层

| 类 | 职责 |
|----|------|
| `service.StudyService` | BMI/BMR/TDEE 计算、用户档案、训练计划、Prompt 上下文生成 |
| `service.WorkoutLogService` | 训练日志 CRUD、热量自动计算、连续打卡统计 |
| `service.NutritionLogService` | 饮食记录 CRUD、食物数据库查询、每日宏量素汇总 |
| `service.AchievementService` | 16 种成就定义、条件检查、`awardAchievement()` |
| `service.ChallengeService` | 挑战 CRUD、AI 推荐、排行榜 |
| `service.BodyMeasurementService` | 围度记录 CRUD、趋势分析、WHR 计算 |
| `service.SleepLogService` | 睡眠记录 CRUD、周统计、干扰因素分析、本地提示生成 |

### 2.4 AI 模块

#### 策略模式（LLM 提供商）

| 类 | 职责 |
|----|------|
| `ai.strategy.LlmStrategy` | 接口：`completions()`, `streamComplete()`, `capabilities()` |
| `ai.strategy.AbstractLlmStrategy` | 模板基类：HTTP 请求发送、响应提取、JSON 转义 |
| `ai.strategy.QwenStrategy` | 通义千问实现，额外提供 `completeWithWebSearch()`, `classify()`, `summarize()` |
| `ai.strategy.DeepSeekStrategy` | DeepSeek 实现，支持 R1 推理链提取 `completeWithReasoning()` |
| `ai.strategy.KimiStrategy` | Kimi 实现，自动选择 8k/32k/128k 上下文模型 |
| `ai.strategy.MultiModelStrategy` | 多模型路由（轮询/故障转移/最优匹配） |

#### 装饰器模式（AiClient 增强链）

| 类 | 职责 |
|----|------|
| `ai.decorator.AiClientDecorator` | 抽象装饰器基类，`unwrap()`, `chainDescription()` |
| `ai.decorator.LoggingAiClientDecorator` | 记录调用次数、总 Token、平均延迟 |
| `ai.decorator.CachingAiClientDecorator` | LRU 缓存（SHA-256 键，TTL 检查），命中率统计 |
| `ai.decorator.RateLimitingAiClientDecorator` | 滑动窗口限流，超限抛 `RateLimitExceededException` |

#### 责任链模式（AI 处理管道）

| 类 | 职责 |
|----|------|
| `ai.pipeline.AiPipeline` | 编排 5 步链，调用 AI，返回增强后的响应 |
| `ai.pipeline.PipelineContext` | 请求/响应状态载体（意图、关键词、安全标志等） |
| `ai.pipeline.PipelineStep` | 接口：`process(ctx, next)`, `stepName()`, `isEnabled()` |
| `ai.pipeline.IntentDetectionStep` | 关键词分类（9类意图）+ 数字实体提取 |
| `ai.pipeline.PromptEnrichmentStep` | 添加时间戳、意图标签、关键词注释、格式指引 |
| `ai.pipeline.ContextInjectionStep` | 注入用户档案上下文和安全声明 |
| `ai.pipeline.SafetyFilterStep` | 拦截违禁药物、极端节食、危险建议 |
| `ai.pipeline.ResponsePostProcessStep` | 提取行动建议、添加免责声明、检测极端数值 |

#### Agent 模式（Template Method + Factory）

| 类 | 职责 |
|----|------|
| `agent.AbstractAgent` | 模板方法基类：`reply()` 骨架调用 `systemPrompt()`, `buildMessage()` |
| `agent.AgentFactory` | 工厂方法：`create(type)` 按字符串类型实例化 Agent |
| `agent.NutritionAgent` | 宏量素计算专家系统提示 |
| `agent.RecoveryAgent` | 恢复建议 Agent（训练负荷评估） |
| `agent.MindsetAgent` | 运动心理 Agent（参与度分析） |
| `agent.ProgressAgent` | 进度分析 Agent（数据完整度评分） |
| `agent.ChallengeAgent` | 挑战推荐 Agent（按训练模式匹配） |

### 2.5 工具类

| 类 | 职责 |
|----|------|
| `util.BmiCalculator` | BMI 计算（中国/WHO双标准）、BMR (Mifflin-St Jeor)、TDEE |
| `util.CalorieCalculator` | 宏量素分配（三阶段）、35+ 运动热量、100+ 食物数据库 |
| `util.WorkoutRecommender` | 35+ 动作库（含肌群映射）、周计划生成 |
| `util.TextAnalyzer` | 文本分析：数字实体、情感、健身术语识别、中英文检测 |

---

## 3. 设计模式说明

### 3.1 策略模式（Strategy Pattern）

**问题**：需要支持多个 LLM 提供商（Qwen / DeepSeek / Kimi），且每个提供商的 API 请求格式、参数、特性均不同。

**解决方案**：定义 `LlmStrategy` 接口，每个提供商实现为独立策略类，客户端（`AppConfig`）在组装时注入具体策略。

```
         «interface»
        LlmStrategy
       ┌────────────┐
       │completions()│
       │streamComplete()│
       └────────────┘
          △  △  △
          │  │  │
   ┌──────┘  │  └──────────┐
   │         │             │
QwenStrategy │    KimiStrategy
          DeepSeekStrategy
```

**优点**：新增 LLM 提供商只需实现 `LlmStrategy` 接口，无需修改任何调用方代码（对修改封闭，对扩展开放）。

---

### 3.2 装饰器模式（Decorator Pattern）

**问题**：需要对 AI 客户端调用透明地添加日志、缓存、限流功能，且这些功能应可自由组合/拆卸。

**解决方案**：`AiClientDecorator` 抽象装饰器包装 `AiClient`，三个具体装饰器按需嵌套。

```
AiClient
   ▲
AiClientDecorator（包装另一个 AiClient）
   ├── LoggingAiClientDecorator
   ├── CachingAiClientDecorator
   └── RateLimitingAiClientDecorator
```

**组装顺序**（AppConfig）：
```
RateLimiting → Caching → Logging → 具体 LlmStrategy
```
请求从外到内穿透装饰器链，响应从内向外返回。

**优点**：每个横切关注点（日志/缓存/限流）各自独立，可在运行时灵活组合，完全不修改 `LlmStrategy` 实现类。

---

### 3.3 责任链模式（Chain of Responsibility Pattern）

**问题**：AI 输入处理需要依次经过多个独立步骤（意图识别、增强、注入、过滤、后处理），每步可独立启用/禁用，中途可短路。

**解决方案**：`PipelineStep` 接口定义 `process(ctx, next)` 方法，`AiPipeline` 按顺序串联各步骤。

```
IntentDetection → PromptEnrichment → ContextInjection → SafetyFilter → ResponsePostProcess
                                                              │
                                              （违规则在此短路，不调用 LLM）
```

**关键特性**：
- 每步通过 `isEnabled()` 控制启停（配置文件开关）
- `SafetyFilterStep` 可设置 `ctx.setBlocked(true)` 短路后续处理
- `PipelineContext` 在链中传递，每步都可读写

**优点**：添加/移除/重排处理步骤不影响其他步骤，完全符合单一职责原则。

---

### 3.4 工厂方法模式（Factory Method Pattern）

**问题**：`AgentFactory.create(type)` 需要根据字符串类型动态创建 9 种 Agent，调用方不应知道具体类名。

**解决方案**：
```java
// AgentFactory.java（工厂方法）
public AbstractAgent create(String type) {
    return switch (type.toUpperCase()) {
        case "NUTRITION"  -> new NutritionAgent(aiClient, studyService);
        case "RECOVERY"   -> new RecoveryAgent(aiClient, studyService);
        case "MINDSET"    -> new MindsetAgent(aiClient, studyService);
        // ... 其余类型
        default           -> new StudyAgent(aiClient, studyService);
    };
}
```

同样，`AppConfig.aiClient()` Bean 方法依据 `ai.default-provider` 配置，工厂式地组装完整的装饰器链。

---

### 3.5 模板方法模式（Template Method Pattern）

**问题**：所有 Agent 的 `reply()` 流程相同（构建系统提示 → 构建消息 → 调用 AI → 包装结果），但每种 Agent 的具体提示词不同。

**解决方案**：`AbstractAgent.reply()` 定义算法骨架，子类只需实现 `systemPrompt()` 和可选的 `buildMessage()`。

```java
// AbstractAgent.java — 模板方法
public final AgentReply reply(String message, UserProfile user) throws Exception {
    String system  = systemPrompt(user);   // ← 抽象方法，子类实现
    String prompt  = buildMessage(message, user); // ← 可重写
    String answer  = ai.chat(system, prompt);
    return new AgentReply(type(), answer, nextActions(), usedContext(), ai.name());
}
```

同理，`AbstractLlmStrategy.complete()` 定义 HTTP 请求骨架，`buildRequestBody()` 由 Qwen/DeepSeek/Kimi 各自实现。

---

### 3.6 观察者模式（Observer Pattern）

**应用位置**：`LearningEventBus`

**解决方案**：使用 `Consumer<LearningEvent>` 函数式监听器，支持 `subscribe()` 注册和 `publish()` 广播。

```java
// 发布事件（训练完成后）
eventBus.publish(new LearningEvent("WORKOUT_LOGGED", userId, data));

// 订阅（成就检查器订阅训练事件）
eventBus.subscribe("WORKOUT_LOGGED", event -> achievementService.checkOnWorkout(event));
```

---

## 4. 数据库设计

### 4.1 核心表（共 12 张）

```
user_profiles          用户档案（身高/体重/目标/训练模式）
study_plans            训练计划（AI生成/用户自定义）
workout_logs           训练日志（动作/组次/热量）
body_measurements      身体测量（7种围度+体脂）
nutrition_logs         饮食记录（食物/宏量素/餐次）
sleep_logs             睡眠日志（时长/质量/干扰因素）
challenges             挑战定义（名称/目标/奖励）
user_challenges        用户参与挑战（进度/打卡记录）
achievements           成就定义（16种）
user_achievements      用户获得成就（时间戳/积分）
ai_conversations       AI对话会话（按用户/Agent分组）
ai_messages            AI消息记录（含Token统计）
```

### 4.2 AI 处理管道数据流

```
用户请求 → PipelineContext（输入区）
                 ↓
           IntentDetectionStep
           → ctx.detectedIntent = "WEIGHT_LOSS"
           → ctx.keywords = ["减脂","体重"]
                 ↓
           PromptEnrichmentStep
           → ctx.enrichedPrompt = "[意图:减脂] [时间:2026-06-10] ..."
                 ↓
           ContextInjectionStep
           → ctx.systemPrompt += "用户档案: 身高175cm, 体重72kg..."
                 ↓
           SafetyFilterStep
           → 通过（无违规内容）
                 ↓
           [LLM API 调用]
           → ctx.rawResponse = "..."
                 ↓
           ResponsePostProcessStep
           → ctx.processedResponse = "..." + 行动建议 + 免责声明
```

---

## 5. 非功能性设计

### 5.1 性能优化

| 机制 | 实现 | 效果 |
|------|------|------|
| LRU 缓存 | `CachingAiClientDecorator`（500条，TTL 60分钟） | 重复问题 0 ms响应 |
| 流式输出 | SSE + 分块传输 | 消除等待感，首字延迟<1s |
| 异步调用 | `@Async` + `ThreadPoolTaskExecutor` | AI 调用不阻塞主线程 |
| 连接池 | Spring Boot 默认 HikariCP | 数据库连接复用 |

### 5.2 安全设计

| 风险 | 防护措施 |
|------|---------|
| 提示词注入 | `SafetyFilterStep` 正则过滤 + 关键词黑名单 |
| API 滥用 | `RateLimitingAiClientDecorator` 每分钟30次限流 |
| SQL 注入 | 全程使用 `JdbcTemplate` 参数化查询 |
| XSS | 前端输出使用 `textContent` 而非 `innerHTML`（AI回复部分） |

### 5.3 可扩展性设计

- **新增 LLM**：实现 `LlmStrategy` 接口，注册到 `AppConfig.aiClient()` 即可
- **新增 Agent**：继承 `AbstractAgent`，在 `AgentFactory.create()` 添加 case
- **新增管道步骤**：实现 `PipelineStep`，注入到 `AiPipeline` 的步骤列表
- **切换数据库**：修改 `DB_MODE` 环境变量，`AppConfig.database()` 自动切换

---

## 6. 代码规模统计

| 类别 | 文件数 | 代码行数（估算） |
|------|--------|----------------|
| Spring 配置 | 3 | ~400 |
| REST 控制器 | 7 | ~1,200 |
| 业务服务 | 7 | ~2,000 |
| AI 策略 | 6 | ~1,500 |
| AI 装饰器 | 4 | ~600 |
| AI 管道 | 7 | ~900 |
| Agent 实现 | 7 | ~800 |
| 工具类 | 4 | ~1,200 |
| 数据模型 | 5 | ~300 |
| 前端页面（HTML/JS） | 20 | ~6,000 |
| 配置/SQL | 4 | ~300 |
| **合计（自定义代码）** | **74+** | **15,200+** |

---

*SmartFitAgent v1.0.0 — 软件设计文档*
