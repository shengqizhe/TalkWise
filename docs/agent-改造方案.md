# Agent 化改造方案（定稿 v1.0）

> 项目定位：求职/毕设作品。本文档是 Agent 化改造的唯一实施依据，所有决策均经多轮评审确认。
> 状态：方案定稿，等待启动信号后按阶段实施。

## 1. 定位与目标

- **定位**：本项目是求职/毕设作品（知讲 TalkWise，Spring Boot 3.1.5 + Vue 3 + MySQL）。
- **目标**：保留现有 Web 系统与数据库，新建独立 Agent 模块，把现有"假 AI 助手"（关键词 if-else + prompt 拼接，`AiAssistantServiceImpl`）替换为真正的 Agent——代码提供工具与循环，**把"下一步做什么"的决策权交给模型**。
- **改造后一句话**：学生/教师/管理员通过对话完成自然语言查数据、讲座咨询、容量建议等任务，写操作经人机确认后执行。

## 2. 范围与非目标

**保留（不 Agent 化）**：Vue 前端页面、MySQL schema、业务 Service 层（讲座/报名/签到/评价）、WebSocket 通知基建。

**重写**：AI 后端整体重写为独立 `agent` 模块，删除旧 `AiAssistantServiceImpl` 的 if-else + prompt 拼接实现（无保留价值）。前端弹窗保留外观，消息模型升级。

**非目标**：
- 不推翻业务 CRUD 架构，业务模块不反向依赖 agent 包
- 不把全部操作塞进对话（高频确定性操作保留表单）
- 不引入 Python 服务（默认 Java 内 LangChain4j）
- 不做多 Agent 编排平台

## 3. 技术选型

| 项 | 选择 | 说明 |
|----|------|------|
| 框架 | Java 17 + Spring Boot 3.1.5（现有）+ LangChain4j | 不升级 Spring Boot；`@Tool` + `AiServices` 提供工具注册与 function calling |
| 模型 | 通义千问 qwen-turbo（现有 DashScope 兼容接口） | 支持 function calling；API key 从代码迁到 `application.yml`（当前硬编码 `AiAssistantServiceImpl.java:42`） |
| 流式 | SSE（SseEmitter） | 事件流：token / tool_call / tool_result / done |
| 实时推送 | 复用现有 WebSocket | 主动任务结果（报表、提醒）推送 |

## 4. Agent 模块结构（新增包）

```
com.example.lecture.agent/
├── core/
│   ├── AgentEngine.java          # 对话循环引擎（唯一入口）
│   ├── ToolRegistry.java         # 工具注册表：扫描 @AgentTool 注解生成 schema
│   ├── ToolExecutor.java         # 执行器：角色校验 → READ/WRITE 分级 → 确认拦截
│   └── AgentSessionManager.java  # 会话加载/保存
├── tool/                         # 业务工具：只包装现有 Service，不写 SQL
│   ├── LectureSearchTools.java   # searchLectures / getLectureDetail
│   ├── RegistrationTools.java    # getMyRegistrations / register / cancel
│   ├── ScheduleTools.java        # getMySchedule / getMyAttendance
│   ├── StatisticsTools.java      # queryStatistics（自然语言查数据）
│   ├── CapacityTools.java        # 容量规划（统计×语义混合）
│   └── LlmFeatureTools.java      # 单步功能点：文案生成 / 情感分析
├── flow/
│   ├── ConfirmationManager.java  # 确认审批流状态机
│   └── PendingAction.java
├── task/                         # 主动任务（报表生成等，L4，可选阶段）
│   ├── AgentTaskService.java
│   └── ReportTaskRunner.java
├── memory/
│   └── ChatMemoryStore.java      # 会话历史持久化（替代前端拼 context）
├── web/
│   ├── AgentChatController.java  # REST + SSE
│   └── AgentWebSocketHandler.java
└── dto/
```

**边界**：agent 包只允许依赖现有 `service` / `mapper` / `entity`，可独立测试、独立下线。

## 5. 引擎机制

### 5.1 对话循环

```
用户消息 → 加载会话历史(最近N轮) + 用户画像(兴趣标签)
  → 模型调用（携带全部工具定义）
  → 二选一：
    a) 工具调用请求 → 校验(角色/参数) → READ 直接执行 / WRITE 走确认拦截
       → 工具结果以 system 消息回填 → 回模型（最多 8 轮）
    b) 最终文本 → 返回前端
  → 终止：最终文本 / 超过8轮 / 超时60s / 工具连续失败3次（给模型换策略机会）
```

**模型的决策节点**（代码不参与）：调哪个工具、传什么参数、结果不满意时改条件重试、何时收尾。

### 5.2 工具分级与权限

```java
@AgentTool(name = "searchLectures", description = "按时间/主题/讲师/关键词查询已发布讲座",
           type = ToolType.READ)
public List<LectureCard> searchLectures(String time, String keyword, String speaker) { ... }

@AgentTool(name = "registerLecture", description = "为当前用户报名指定讲座",
           type = ToolType.WRITE)          // WRITE 强制走确认流
@RequireRole("student")
public String registerLecture(Long lectureId) { ... }
```

### 5.3 写操作直接执行（用户明确指令即授权）

```
模型请求 WRITE 工具（用户明确要求报名/取消等）
  → 引擎记录审计日志（用户、工具、参数）
  → 直接执行工具 → 结果回填会话 → 模型如实告知执行结果
```

**约束**：写操作仅限当前登录用户自己的数据（身份来自 AgentContext，不接受模型传参）；
业务规则校验（防重复、容量、截止时间）由 Service 层兜底；
执行结果必须如实回执、不允许编造；系统提示词要求"意图不明确时先询问，明确指令才执行"。

### 5.4 会话与追踪

- 会话：按用户建会话，消息落库，上下文超长时摘要旧消息
- 追踪：每次模型调用记录请求/响应/工具调用/耗时/token/状态，提供查询接口（调试与演示"AI 怎么想的"）

## 6. 功能版图（最新确认版）

### 6.1 Agent 化（多步 + 语义决策，核心）

| # | 功能 | 实现要点 | 角色 |
|---|------|---------|------|
| 1 | **自然语言数据问答** | `queryStatistics` 工具：模型把自然语言映射到预定义指标，**不生成 SQL** | 管理员/教师 |
| 2 | **对话式讲座查询/推荐** | `searchLectures` 意图→工具参数；排序用规则代码，LLM 生成推荐理由与多轮修正 | 学生 |
| 3 | **容量规划**（详见 §7） | 统计（历史满座率）× 语义（LLM 评估内容重要性/讲师声望）融合，教师确认 | 教师 |
| 4 | 报名 / 取消报名 | 工具包装现有 Service；用户明确指令时直接执行，审计日志留痕 | 学生 |
| 5 | 自动答疑（可选） | RAG，**取决于讲座资料数据量，数据薄则不做** | 学生 |

### 6.2 单步 LLM 功能点（不建多步循环）

- 讲座宣传文案 / SEO 标题生成（替换 `PromotionContentUtil` 模板拼接）
- 评价情感分析 + 改进建议（落库 `evaluation.sentiment_score` 等空置字段）
- 提醒文案生成

### 6.3 确定性代码（不 Agent 化）

- 候补递补：统计打分排序（历史签到率 × 参与度评分 × 兴趣匹配），可选 embedding 兴趣匹配；LLM 只生成递补通知文案
- "我的本周安排"聚合：代码查两张表按天排序，LLM 可选生成摘要
- 定时状态流转（`@Scheduled` 已实现）、管理 CRUD、地图导航

## 7. 容量规划场景设计（统计 × 语义混合）

**为什么需要 LLM**：冷启动场景（新讲师/新主题无历史数据）下统计失效；讲座内容在专业层面的重要性、讲师的社会声望只能从文本语义判断。

| 维度 | 数据来源 | 实现 |
|------|---------|------|
| 该讲师历史满座率 | 报名/签到历史 | 统计代码（有数据时） |
| 同主题历史热度 | 讲座表分类聚合 | 统计代码（有数据时） |
| 讲座内容重要性 | 标题/简介文本 | LLM 语义评估（热点话题/前沿方向） |
| 讲师社会知名度 | 讲师头衔/简介 | LLM 语义评估 |
| 讲师校内欢迎度 | 历史评价/签到率 | 统计为主 + 评价文本 LLM 提炼 |

**轨迹**（教师："我打算开一场《…》讲座，定多少容量合适？"）：

```
轮1  → getLectureDetail(内容)
轮2  → getTeacherProfile(讲师)          # 讲师资料/历史讲座
轮3  → getHistoryStats(讲师+分类)       # 历史满座率（可能为空 → 冷启动）
轮4  模型语义评估 → 定性等级输出：内容热度【高/中/低】 讲师声望【高/中/低】
轮5  融合规则：统计值 × 语义等级权重（冷启动时语义权重高）→ 建议容量
     → 输出："建议 400 人（大报告厅），依据：…" → 教师确认 → 创建讲座
```

**约束**：LLM 只输出定性等级（高/中/低），容量数字由规则融合计算；必须教师确认；依据须可解释。

## 8. 多步场景轨迹（其余）

### 8.1 自然语言查数据（核心演示）

```
管理员："这个月哪个系报名最活跃？"
轮1  模型 → queryStatistics(指标=报名数, 维度=系别, 时间=本月)
轮2  结果为空 → 模型自主换策略：改为近三个月 → 有数据
轮3  → 组织回答 + 可选"生成报表"（写操作走确认）
```

### 8.2 讲座推荐顾问

```
学生："推荐这周末值得去的讲座，要留时间复习"
轮1  模型 → searchLectures(周末) + getMySchedule(本周)   # 并行/顺序自主决定
轮2  → 冲突过滤（周六上午有课）→ getMyRegistrations() 排除已报
轮3  规则排序（兴趣标签）→ LLM 生成推荐理由 → 反问"需要报名吗？"
学生："报《大模型实践》" → registerLecture → 引擎直接执行 → 如实回执"已报名成功"
```

## 9. 数据设计（新增 5 张表，不动现有表）

| 表 | 用途 |
|----|------|
| `agent_session` | 会话（id, user_id, title, created_at, updated_at） |
| `agent_message` | 消息（id, session_id, role, content, tool_calls JSON） |
| `pending_action` | ~~已废弃~~（设计变更为写操作直接执行，不再需要待确认动作表） |
| `agent_trace` | 执行日志（id, session_id, turn_no, request/response/tool_calls JSON, latency_ms, tokens, status） |
| `agent_task` | 主动任务（id, type, params JSON, status, result JSON, 时间戳） |

## 10. 接口设计

- `POST /api/agent/chat` → SSE 流，事件：`token` / `tool_call` / `tool_result` / `done`
- `GET /api/agent/sessions`、`GET /api/agent/traces/{sessionId}`
- `POST /api/agent/tasks`（报表任务，可选阶段）
- `WS /ws/agent`：主动任务结果推送

旧接口 `/api/ai/*` 改造完成后下线或兼容转发。

## 11. 前端改造

- 消息模型升级：`{type: text | tool_call}`（展示工具调用状态；无确认卡片）
- 弹窗组件抽成 `AgentChatPanel.vue`，`FloatingAiBot.vue` 与独立页 `StudentAi.vue` 共用
- 执行回执：写操作完成后以文本如实回显结果

## 12. 实施阶段（最新版）

| 阶段 | 内容 | 状态 |
|------|------|------|
| A | 后端最小闭环：对话引擎 + 工具注册表 + 4 个工具（searchLectures / getMyRegistrations / registerLecture / cancelRegistration，写操作直接执行）+ 审计日志 + API key 迁配置 | ✅ 已完成 |
| B | 前端接入：悬浮球切 `/api/agent/chat`、工具调用轨迹展示、管理员控制台 AI 入口 | ✅ 已完成 |
| 加固 | 同用户会话串行化（ReentrantLock + 30s 等待上限，防并发请求交错污染会话历史） | ✅ 已完成 |
| C | Agent 能力扩展：① 统计问答工具 queryStatistics（管理员/教师自然语言查数据，映射预定义指标、不生成 SQL）；② 单步功能点：讲座文案生成（替换 PromotionContentUtil）、评价情感分析（落库空置字段） | 下一步 |
| D | 容量规划（统计×语义混合 + 教师确认）：统计查历史满座率，LLM 评估内容重要性/讲师声望出定性等级，规则融合出建议容量 | 待办 |
| E | 持久化与可观测（上线前必做）：agent_session / agent_message / agent_trace 落表替代内存会话；执行追踪查询接口；旧 `/api/ai/*` 下线 | 待办 |
| F | 可选进阶：SSE 流式输出；RAG 答疑（先评估讲座资料数据量，薄则不做）；主动任务（定时提醒、报表推送） | 可选 |

**当前架构要点**（供演示/面试）：
- 单 Agent：一个决策循环 + 工具集，角色差异靠工具权限而非多 Agent
- 决策权在模型（调什么工具/传什么参数/何时收尾），执行权与守门在代码（身份取登录态、Service 层校验、审计日志）
- 会话按 userId 隔离 + 每用户锁串行化；内存版会话，E 阶段落库

## 13. 求职/简历表述建议

> 独立设计并实现 Agent 模块：工具注册表（注解扫描）+ 多轮决策循环 + 写操作直接执行（登录态身份透传 + 审计日志）+ 会话隔离与串行化，集成 DeepSeek function calling（LangChain4j）；实现对话式查讲座/报名/取消，规划统计问答与"统计×语义"混合容量预估，覆盖学生/教师/管理员三类角色。

面试主动讲的设计决策：决策权与执行权分离（模型提议、代码守门）；写操作为什么可以"明确指令直接执行"（身份不传参 + Service 保底 + 审计日志）；LLM 为什么只输出定性等级不做数值预测；统计与语义何时分工（冷启动）；会话隔离与并发串行化；agent 模块边界与可观测性。

## 14. 关键约束清单

1. agent 模块不写 SQL，只调现有 Service
2. 写操作直接执行（用户明确指令即授权）：身份只取当前登录用户，审计日志留痕，Service 层保底校验
3. 不动现有业务表结构，只新增 agent 表
4. API key 不留在源码
5. 会话按用户隔离，同一用户请求串行化
6. 每次模型调用落 trace（E 阶段）
7. 实施按 §12 阶段表推进（A / B / 加固 已完成）
