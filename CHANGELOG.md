# 更新日志（Changelog）

**知讲 TalkWise** — 大学讲座智能平台（Spring Boot 3 + Vue 3 + LangChain4j Agent）

> 格式约定：每次提交后在顶部追加一条，按「新特性 / 变更 / 修复」分类说明；
> 每条附提交短哈希，便于与 git 历史对照。

---

## [未发布] Agent 阶段 C（计划中）

- 单步 LLM 功能点：讲座宣传文案生成、评价情感分析（落库空置字段）
- 容量规划（统计 × 语义混合 + 教师确认）
- 会话/消息/trace 落库（替代内存会话）

---

## 2026-09-08 · Agent 阶段 B：前端接入

**新特性**
- 悬浮球（FloatingAiBot）切换至 `/api/agent/chat`：纯对话式交互，报名/取消在对话中直接完成
- **工具调用轨迹展示**：回复下方以标签显示「🔧 查询讲座 / 报名讲座 / 取消报名」，直观呈现"AI 做了什么"
- 管理员控制台（AdminLayout）挂载 AI 助手入口（管理员可用自然语言查数据）
- 后端 `AgentChatResponse` 新增 `tools` 字段（工具名 + 参数摘要）；`AgentEngine` 返回结构化结果 `ChatResult{reply, tools}`

**变更**
- 前端新增 `api/agent.js`（超时放宽至 60s，适配多轮工具调用）；
- `StudentAi.vue` 同步切换新接口
- 旧 `/api/ai/*` 接口保留并行（验证通过后下线）

---

## 2026-09-08 · 写操作直接执行（`d056af3`）

**变更**
- `AgentEngine`：WRITE 工具从"拦截占位"改为**直接执行**；执行前记录审计日志（用户、工具、参数）
- 系统提示词更新：用户明确指令直接执行并如实回告、意图不明确先询问、禁止编造执行结果
- 登记为写操作安全底线：身份只取当前登录用户（防越权）、Service 层保底校验（防重复/容量/时间）、全程审计留痕

**新特性**
- 新增写工具 `registerLecture`（对话式报名）、`cancelRegistration`（对话式取消报名）
- 查询工具输出补充讲座 ID（写工具定位讲座所必需）

**文档**
- `docs/agent-改造方案.md` 同步 8 处：确认审批流 → 写操作直接执行（§5.3 及功能版图、场景轨迹、数据表、接口、前端、阶段表、约束清单）

---

## 2026-09-08 · 模型切换 DeepSeek（`d7d8b25`）

**变更**
- LLM 从通义千问（qwen-turbo @ DashScope）切换为 **DeepSeek（deepseek-v4-flash @ api.deepseek.com）**，OpenAI 兼容协议
- 配置支持 `OPENAI_API_KEY` / `OPENAI_MODEL` / `OPENAI_BASE_URL` 环境变量覆盖；Agent 引擎与旧 `/api/ai` 接口共用同一配置

---

## 2026-09-08 · Agent 阶段 A：最小闭环（`6f80c75`）

**新特性**
- 新增 `com.example.lecture.agent` 模块（11 个文件）：
  - `@AgentTool` / `@AgentParam` 注解 + `AgentToolRegistry`：启动扫描工具方法，模型参数 JSON 自动注入并反射执行——新增工具 = 加一个注解方法
  - `AgentEngine`：多轮对话循环（模型→工具执行→结果回填→再问模型，最多 8 轮），内存会话窗口（最近 20 条），工具结果超长自动截断
  - `AgentContext`：登录用户身份 ThreadLocal 透传，工具不接受模型传入的 userId（防越权）
  - `AgentProperties`：`agent.*` 配置映射
- 两个只读工具：`searchLectures`（查讲座）、`getMyRegistrations`（查我的报名）
- 新接口 `POST /api/agent/chat`：Sa-Token 取登录用户；游客可咨询讲座类问题

**变更**
- `pom.xml`：引入 LangChain4j 0.35.0（core + open-ai，不引 spring starter 以避免与 Spring Boot 3.1.5 耦合）
- `application.yml`：新增 `agent.*` 配置段
- `AiAssistantServiceImpl`：API key 从源码硬编码迁移至配置——**源码不再包含任何密钥**

---

## 2026-09-08 · 初始提交：UI 重构与品牌命名（`11cbb2a`）

**新特性**
- 全站黑白极简 UI 重构（对齐设计稿）：
  - 学生端：首页（Hero + 插图）、讲座列表（筛选 + 卡片网格）、我的预约、个人中心、讲座详情、AI 页
  - 教师端：工作台（统计 + 近期讲座）、我的讲座（表格管理 + 创建/编辑对话框）、预约名单（签到管理）、历史记录、个人中心、评价反馈
  - 管理员端：控制台布局（顶部导航 + 左侧栏）、仪表盘、审核队列、发布管理、分类管理、数据报表（CSV 导出）、权限设置、系统通知
  - 公共页：首页（Hero）、登录、注册、找回密码
- 全局主题：Element Plus 主色改黑 + 统一卡片/表格/按钮样式（`styles/theme.css`）
- 品牌命名：**知讲 TalkWise**（中英文名，登录页/顶栏/文档全站同步）
- 删除地图导航功能（页面、路由、入口）

**修复**
- `RoleManagement` 的 import 路径错误（`../api` → `../../api`，此前无法编译）
- 修改密码接口传参错误（改为传表单对象）

**文档**
- `docs/agent-改造方案.md`：Agent 化改造定稿（工具注册/对话循环/容量规划/数据设计/实施阶段）

**工程**
- `.gitignore` 补充前端 `node_modules` / `dist`
- 求职简历 PDF 已从全部 git 历史中移除（`git filter-branch` 重写 + 强推）
