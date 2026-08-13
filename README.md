# SmartFitAgent — AI 增强健身规划平台

> **班级**：2024211310　**姓名**：崔天睿　**学号**：2024211255

基于 Java Spring Boot 3.2 的全栈 AI 健身管理平台，集成 DeepSeek / Qwen / Kimi 大语言模型，提供 20 个功能页面、6 种设计模式、完整 MVC 架构。

---

## 快速启动

**环境要求**：JDK 17+、Maven 3.6+

```bash
cd SmartFitAgent
mvn spring-boot:run
```

浏览器打开：<http://localhost:8080>

---

## 接入真实 AI（DeepSeek）

在 `src/main/resources/application.yml` 中修改两处：

```yaml
ai:
  provider: ${AI_PROVIDER:deepseek}   # 从 mock 改为 deepseek
  deepseek:
    api-key: sk-你的密钥               # 填入 DeepSeek API Key
```

重启后控制台出现 `AI 核心客户端: deepseek:deepseek-chat` 即表示接入成功。

也支持 Qwen、Kimi，修改对应字段即可。

---

## 项目结构

```
SmartFitAgent/
├── src/main/java/com/smartfitagent/
│   ├── spring/          # Spring Boot 启动与配置
│   ├── controller/      # REST 控制器（MVC Controller 层）
│   ├── service/         # 业务服务层
│   ├── agent/           # AI Agent（策略 + 模板方法模式）
│   ├── ai/              # AI 客户端（策略 + 装饰器模式）
│   │   ├── strategy/    # DeepSeek / Qwen / Kimi / Mock 策略
│   │   ├── decorator/   # 日志 / 缓存 / 限速装饰器
│   │   └── pipeline/    # AI 处理管道
│   ├── db/              # 数据访问层（Database 接口 + 实现）
│   ├── model/           # 数据模型
│   └── event/           # 观察者事件总线
├── public/
│   ├── assets/          # styles.css、layout.js
│   └── pages/           # 20 个 HTML 功能页面
├── src/main/resources/
│   ├── application.yml  # 主配置
│   ├── schema.sql       # 建表脚本
│   └── data.sql         # 初始数据
└── docs/                # 设计文档、功能说明、安装说明
```

---

## 20 个功能页面

| # | 页面 | 说明 |
|---|------|------|
| 1 | dashboard | 健身总览仪表盘 |
| 2 | profile | 个人档案与 BMI 计算 |
| 3 | tutor | AI 教练多 Agent 对话 |
| 4 | workout | 训练日志记录 |
| 5 | nutrition | 饮食营养追踪 |
| 6 | measurements | 身体围度测量 |
| 7 | sleep | 睡眠质量记录 |
| 8 | planner | 周训练计划制定 |
| 9 | analytics | 数据分析可视化 |
| 10 | health | 健康状态评估 |
| 11 | notes | 训练日记 |
| 12 | courses | 健身课程库 |
| 13 | quiz | 体能知识测验 |
| 14 | writing | AI 日志与训练记录 |
| 15 | vocab | 动作库（16 个核心动作） |
| 16 | mistakes | 风险纠正与伤病预防 |
| 17 | pomodoro | 训练计时器 |
| 18 | challenges | 挑战赛广场 |
| 19 | achievements | 成就系统 |
| 20 | community | 健身社区 |

---

## 设计模式（6 种）

| 模式 | 实现位置 |
|------|---------|
| MVC | `controller/` + `public/pages/` + `model/` |
| 策略模式 | `ai/strategy/`：DeepSeekStrategy、QwenStrategy、KimiStrategy、MockAiClient |
| 装饰器模式 | `ai/decorator/`：Logging → Caching → RateLimiting 三层装饰 |
| 工厂模式 | `agent/AgentFactory`：按类型创建对应 Agent |
| 观察者模式 | `event/LearningEventBus`：数据变更事件广播 |
| 模板方法模式 | `agent/AbstractAgent`：固定 reply 流程，子类定制 systemPrompt |

---

## 技术栈

- **后端**：Java 17+、Spring Boot 3.2.5、Spring MVC、JdbcTemplate
- **数据库**：H2（开发内存库，MySQL 模式兼容）/ MySQL 8
- **AI**：DeepSeek API、Qwen API、Kimi API（OpenAI 兼容格式）
- **前端**：原生 HTML5 / CSS3 / JavaScript（无框架），深色主题
- **构建**：Maven 3.6+

---

## 代码规模

- Java 源文件：**81 个**
- Java 代码行数：**17,256 行**
- 前端页面：**20 个**
- 文档：**8 个 Markdown 文件**
