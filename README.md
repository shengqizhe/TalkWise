# 知讲 TalkWise

> 大学讲座智能平台 —— 三端管理系统 + 对话式 AI Agent

知讲 TalkWise 是一套面向高校的讲座全流程管理平台：学生预约讲座、教师发布与管理讲座、管理员统一运营，并且内置一个**能真正干活的 AI 助手**——用自然语言查讲座、看报名、直接报名或取消，全程由 Agent 自主调用工具完成。

---

## ✨ 功能特性

### 学生端
- 首页（Hero + 插图）、讲座列表（主题/形式筛选 + 卡片网格）
- 讲座详情、在线预约 / 取消预约、我的预约（**签到、评价讲座**）
- 个人中心（资料编辑）、消息通知

### 教师端（工作台）
- 工作台：进行中/累计预约/已完结/签到统计 + 近期待开展讲座
- 我的讲座：创建 / 编辑 / 发布 / 下架 / 删除（时间地点冲突与状态约束）
- 预约名单：按讲座查看学生名单、标记签到 / 取消签到
- 历史记录（已完结归档）、评价反馈、个人中心

### 管理员端（控制台）
- 仪表盘、讲座审核队列、发布管理
- 用户管理、讲师管理、分类管理（只读）
- 数据报表（预约/签到/取消统计 + CSV 导出）、权限设置、系统通知

### AI Agent 助手（核心亮点）
- **对话式操作**：查讲座、查我的报名、"帮我报名《XX》"、"取消报名"——Agent 自主决定调用哪些工具并执行
- **统计问答**（教师/管理员）："这个月哪个分类最热门？""各院系报名分布？"——自然语言映射预定义指标，不生成 SQL
- **文案生成**（教师）："帮我给《XX》写宣传文案"——单步 LLM 生成，替代模板拼接
- **评价分析**（教师）："分析一下《XX》的评价"——自动分批（Map-Reduce，上千条评价也能处理）从评价中提炼主要问题、正面反馈与可行动的改进建议；数据量大时自动转后台任务（进度可查、完成后通知）
- **写操作直接执行**：用户明确指令即授权（身份取自登录态、业务规则 Service 层兜底、写操作全程审计日志）
- **工具调用轨迹**：回复下方展示 `🔧 查询讲座 / 🔧 报名讲座`，直观呈现"AI 做了什么"
- 三端可用（顶栏 `✨ AI 助手` 入口，右侧抽屉展开，管理员控制台同样接入）

---

## 🛠 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.1.5 · MyBatis-Plus · MySQL 8 · Sa-Token（认证）· WebSocket |
| Agent | LangChain4j 0.35 · DeepSeek（OpenAI 兼容 function calling）· 自研工具注册表与对话循环 |
| 前端 | Vue 3 · Vite · Element Plus · Pinia · Vue Router · Axios |
| 其他 | EasyExcel / POI（导入导出）· Thymeleaf（邮件模板）· springdoc-openapi |

---

## 🏗 架构概览

```
前端（Vue 3，黑白极简风格，三端布局：学生/教师通用顶栏 + 管理员控制台）
   │  REST /api/**（Sa-Token 认证）     顶栏 AI 助手 → POST /api/agent/chat
   ▼
后端（Spring Boot）
   ├── controller / service / mapper      业务 CRUD（讲座/报名/签到/评价/统计）
   └── agent 模块（独立，只调 Service，不写 SQL）
        ├── AgentEngine        对话循环：模型 ↔ 工具执行（轮数上限 `agent.max-turns`，默认 8）
        ├── AgentToolRegistry  扫描 @AgentTool 注解，注册工具并反射执行
        ├── AgentTaskScheduler 主动任务调度：优先级抢占、退避重试、卡死回收
        ├── tools              查讲座/报名/统计/文案/评价/容量/教室推荐/讲座写操作草稿
        └── AgentContext       登录用户身份透传（工具不接受模型传参，防越权）
```

**Agent 设计要点**：决策权在模型（调什么工具、传什么参数、何时收尾），执行权与守门在代码（身份取登录态、Service 层校验、审计日志）；工具带**域标签与角色限制**，调用前经 `ToolRouter` 身份 + 意图双层过滤后下发；会话按 userId 隔离 + 每用户锁串行化。

**写操作走确认闭环**：创建/修改/取消/发布讲座都由 `prepare*` 工具先生成草稿并落 `pending_action`，经用户明确确认后由确认接口执行，带归属校验、字段白名单与条件状态更新防重复执行。容量估算只返回建议，不自动改库。

详见 [docs/agent-改造方案.md](./docs/agent-改造方案.md)。

---

## 🚀 快速开始

### 环境要求
- JDK 17+（命令行注意 `JAVA_HOME` 指向 17）
- MySQL 8+
- Node.js 16+
- Maven 3.6+（使用系统 `mvn`；仓库已移除 `mvnw` / `mvnw.cmd` 与 `.mvn` wrapper）

### 后端启动
```bash
# 1. 初始化数据库：创建库并执行脚本（src/main/resources/db 下）
# 2. 配置环境变量（敏感信息不进代码库）：
#    方式一（推荐）：复制 .env.example 为 .env 并填入真实值（.env 已被 gitignore）
#        Git Bash 加载：  set -a; source .env; set +a
#        IDEA 加载：      见下方「IDEA 中启动（EnvFile 插件）」
#    方式二：手动逐个设置（见下方 Windows 提示）
#    必需项：MYSQL_PASSWORD / MAIL_USERNAME / MAIL_PASSWORD / OPENAI_API_KEY
# 3. 启动
mvn spring-boot:run
# 服务地址 http://localhost:8080/api（Swagger: /api/swagger-ui.html）
```
> Windows 提示：若系统默认 JDK 为 8，请先设置 `set JAVA_HOME=D:\jdk\jdk-17.0.14`（或对应路径）再运行 `mvn`；
> 环境变量示例：`set MYSQL_PASSWORD=你的密码`（其余同理，启动前逐个设置）。

### IDEA 中启动（EnvFile 插件）

IDEA 直接点 Run 启动时不会自动读取 `.env`，会因占位符无法解析而报错：

```
java.lang.IllegalArgumentException: Could not resolve placeholder 'MAIL_USERNAME' in value "${MAIL_USERNAME}"
```

这是因为 `application.yml` 中的 `${MYSQL_PASSWORD}` / `${MAIL_USERNAME}` / `${MAIL_PASSWORD}` / `${OPENAI_API_KEY}` 只由 `.env` 提供，而 Spring Boot 不认识 `.env`，只读系统环境变量、JVM 参数与命令行参数。用 EnvFile 插件即可让 IDEA 一键加载：

1. `Settings` → `Plugins` 搜索 `EnvFile`，安装后重启 IDEA；
2. `Run` → `Edit Configurations` → 选中 `UniversityLectureManagementSystemApplication`；
3. 勾选 `Enable EnvFile`，添加 `.env` 文件路径（项目根目录的 `.env`）；
4. 同时确认该运行配置的 `JRE` 为 JDK 17（`File` → `Project Structure` → `Project SDK` 也建议设为 17）。

完成后点 Run 即可正常启动。

### 前端启动
```bash
cd frontend
npm install
npm run dev     # 开发模式（Vite）
npm run build   # 生产构建
```

### 体验 AI Agent
登录任一角色后，点击顶栏的 `✨ AI 助手` 按钮，右侧会滑出对话抽屉：
- "最近有什么讲座？"
- "帮我报名《讲座名》"
- "我报了哪些讲座？"

---

## 📁 项目结构

```
├── frontend/                        # Vue 3 前端
│   └── src/
│       ├── views/                   # 学生/教师/管理员三端页面
│       ├── layout/                  # 全局布局 + 管理员控制台布局
│       ├── components/              # AI 助手抽屉与对话面板、通知铃铛等
│       └── styles/theme.css         # 黑白主题（Element Plus 变量覆盖）
├── src/main/java/com/example/
│   ├── lecture/                     # 业务代码（controller/service/mapper/entity）
│   │   └── agent/                   # Agent 模块（引擎/工具/注册表/上下文）
│   └── university_lecture_management_system/   # 启动类
├── docs/
│   ├── agent-改造方案.md             # Agent 化方案与实施阶段（持续更新）
│   ├── tool-routing.md              # 工具路由：分层筛选与身份/意图双层过滤设计
│   ├── 容量规划-会话交接.md           # 容量估算实现与后续阶段交接
│   └── dynamic-reminder-guide.md    # 动态提醒配置
├── CHANGELOG.md                     # 变更日志（每次提交追加）
└── README.md                        # 本文件
```

