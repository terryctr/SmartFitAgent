# SmartFitAgent — 软件安装手册

## 1. 系统要求

| 项目 | 最低配置 | 推荐配置 |
|------|---------|---------|
| 操作系统 | Windows 10 / macOS 12 / Ubuntu 20.04 | Windows 11 / macOS 14 / Ubuntu 22.04 |
| JDK | Java 17 | Java 21 |
| 内存 | 2 GB | 4 GB 及以上 |
| 磁盘空间 | 500 MB | 2 GB |
| 数据库（生产） | MySQL 8.0 | MySQL 8.0+ |
| 网络 | 可访问 LLM API | 稳定宽带连接 |
| 构建工具 | Maven 3.8+ | Maven 3.9+ |

---

## 2. 快速启动（开发模式 / H2 内存数据库）

无需安装 MySQL，适合评审演示。

### 步骤 1 — 克隆/解压项目

```bash
# 若使用 Git
git clone <repo_url> SmartFitAgent
cd SmartFitAgent

# 若使用压缩包
unzip SmartFitAgent.zip
cd SmartFitAgent
```

### 步骤 2 — 编译并运行

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

首次运行会自动下载依赖（约 80 MB）。

### 步骤 3 — 访问应用

打开浏览器，访问 `http://localhost:8080`

默认显示主页仪表盘。

---

## 3. 生产部署（MySQL 数据库）

### 步骤 1 — 安装并配置 MySQL

```sql
-- 创建数据库和用户
CREATE DATABASE smart_study_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'agent_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON smart_study_agent.* TO 'agent_user'@'localhost';
FLUSH PRIVILEGES;
```

### 步骤 2 — 配置环境变量

创建 `.env` 文件（或在系统中设置环境变量）：

```properties
DB_MODE=mysql
DB_URL=jdbc:mysql://localhost:3306/smart_study_agent?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
DB_USERNAME=agent_user
DB_PASSWORD=your_password

# LLM API 密钥（至少配置一个）
AI_QWEN_API_KEY=sk-xxxxxxxxxxxxxxxxxxxx
AI_DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxxxxxx
AI_KIMI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxx

# 默认 AI 提供商
AI_DEFAULT_PROVIDER=qwen
```

### 步骤 3 — 初始化数据库表

应用启动时会自动执行 `schema.sql` 和 `data.sql`，或手动执行：

```bash
mysql -u agent_user -p smart_study_agent < src/main/resources/schema.sql
mysql -u agent_user -p smart_study_agent < src/main/resources/data.sql
```

### 步骤 4 — 打包并启动

```bash
./mvnw clean package -DskipTests
java -jar target/smart-fit-agent-1.0.0.jar
```

指定配置文件：
```bash
java -jar target/smart-fit-agent-1.0.0.jar \
  --spring.profiles.active=prod \
  --DB_MODE=mysql \
  --DB_URL=jdbc:mysql://localhost:3306/smart_study_agent
```

---

## 4. 配置 LLM API 密钥

### 通义千问（Qwen）

1. 访问 [阿里云 DashScope 控制台](https://dashscope.aliyun.com/)
2. 注册/登录账号
3. 在「API-KEY 管理」创建新密钥
4. 将密钥填入 `AI_QWEN_API_KEY` 环境变量

### DeepSeek

1. 访问 [DeepSeek 开放平台](https://platform.deepseek.com/)
2. 注册账号并创建 API Key
3. 将密钥填入 `AI_DEEPSEEK_API_KEY` 环境变量

### Kimi（Moonshot AI）

1. 访问 [Moonshot AI 开放平台](https://platform.moonshot.cn/)
2. 注册账号并创建 API Key
3. 将密钥填入 `AI_KIMI_API_KEY` 环境变量

> **提示**：未配置的 API 在调用时返回占位回复，不影响其他功能使用。

---

## 5. 应用配置说明（application.yml）

```yaml
server:
  port: 8080           # 修改此处可更改端口

ai:
  default-provider: qwen    # 可选: qwen, deepseek, kimi, multi
  pipeline:
    intent-detection: true  # 意图识别开关
    enrichment: true        # 提示词增强开关
    safety-filter: true     # 安全过滤开关
  cache:
    enabled: true
    max-size: 500           # 最大缓存条数
    ttl-minutes: 60
  rate-limit:
    enabled: true
    requests-per-minute: 30

spring:
  datasource:
    url: ${DB_URL:jdbc:h2:mem:testdb}
    username: ${DB_USERNAME:sa}
    password: ${DB_PASSWORD:}
```

---

## 6. 目录结构

```
SmartFitAgent/
├── pom.xml                         # Maven 项目描述
├── src/
│   ├── main/
│   │   ├── java/com/smartfitagent/
│   │   │   ├── spring/            # Spring Boot 入口与配置
│   │   │   ├── controller/        # REST 控制器层
│   │   │   ├── service/           # 业务逻辑层
│   │   │   ├── agent/             # AI Agent 实现
│   │   │   ├── ai/                # AI 策略、装饰器、管道
│   │   │   ├── model/             # 数据模型
│   │   │   ├── util/              # 工具类
│   │   │   └── db/               # 数据库访问层
│   │   └── resources/
│   │       ├── application.yml    # 配置文件
│   │       ├── schema.sql         # 数据库建表语句
│   │       └── data.sql          # 初始数据
│   └── test/
└── public/
    ├── index.html                  # 主页（重定向到仪表盘）
    ├── assets/
    │   └── styles.css             # 全局样式
    └── pages/                     # 所有前端页面（20个）
```

---

## 7. 常见问题排查

| 问题 | 原因 | 解决方法 |
|------|------|---------|
| 端口 8080 被占用 | 其他程序占用 | `server.port=8081` 在配置中修改 |
| H2 控制台无法访问 | 路径不对 | 访问 `/h2-console`，JDBC URL 填 `jdbc:h2:mem:testdb` |
| AI 返回「未配置 API」 | 密钥未设置 | 检查环境变量 `AI_*_API_KEY` |
| MySQL 连接失败 | 用户名/密码/数据库名错误 | 检查 `.env` 中的 `DB_URL/DB_USERNAME/DB_PASSWORD` |
| 页面样式丢失 | 静态资源路径问题 | 检查 `public/assets/styles.css` 是否存在 |
| 启动时 ClassNotFoundException | JDK 版本不对 | 确认使用 JDK 17+ |

---

## 8. 验证安装成功

启动后访问以下端点，应返回 JSON：

```
GET http://localhost:8080/api/dashboard   → 仪表盘数据
GET http://localhost:8080/api/agents      → AI Agent 列表
GET http://localhost:8080/api/workout/stats → 训练统计
```

看到 JSON 响应即表示安装成功。

---

*SmartFitAgent v1.0.0 — AI 智能健身助手*
