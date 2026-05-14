# ⚡ PaperAI — Multi-Agent 论文写作协作系统

基于 **Spring Boot 3.4 + Vue 3** 的 AI 论文写作平台，5 个专业 Agent 角色协作完成从选题到完稿的全流程。支持**可视化流程画布**拖拽编排自定义写作管线。

---

## 🎨 界面预览

- **暗色科技风主题** — 紫色渐变 + 粒子背景 + 液态玻璃效果
- **可视化流程画布** — 拖拽 Agent 节点、连线编排、条件分支、回退循环
- **实时执行监控** — 节点染色动画（灰等待/蓝执行中/绿完成/红失败）

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────┐
│              前端 Vue 3 + Vite + Ant Design Vue 4        │
│  Pinia · Vue Router · Axios · KaTeX · markdown-it        │
│  Vue Flow (可视化流程画布) · dagre (DAG 自动布局)         │
│  SSE (Server-Sent Events) 实时接收写作进度                │
└──────────────────────┬──────────────────────────────────┘
                       │ REST API + SSE
┌──────────────────────▼──────────────────────────────────┐
│              后端 Spring Boot 3.4 + Java 21              │
│                                                          │
│  ┌─────────────────┐  ┌─────────────────────────────┐   │
│  │ Controller 层    │  │ Service 层                   │   │
│  │ REST + SSE       │  │ OrchestratorService (引擎)   │   │
│  │ FlowController   │  │ PaperService · AgentTaskSvc  │   │
│  └────────┬────────┘  │ StepEventPublisher (SSE)     │   │
│           │           └──────────────┬──────────────┘   │
│  ┌────────▼──────────────────────────▼──────────────┐   │
│  │           Agent 引擎 (5 个专业角色)                  │   │
│  │  🧭 导师 · 🔬 研究员 · ✍️ 写作者 · 📝 审稿人 · ✨ 润色师│   │
│  │  AgentContext (黑板模式) · FlowProfile (流程定义)    │   │
│  └──────────────────────┬───────────────────────────┘   │
│                         │                                │
│  ┌──────────────────────▼───────────────────────────┐   │
│  │      Spring AI Alibaba DashScope (通义千问)       │   │
│  │      qwen3.6-plus / qwen-max / deepseek-v3        │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─────────────────┐  ┌────────────────────────────┐    │
│  │   MySQL 8.0      │  │  Redis                     │    │
│  │  paper · task    │  │  会话缓存                   │    │
│  │  paper_version   │  │                             │    │
│  │  flow_definition │  │                             │    │
│  └─────────────────┘  └────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

### 技术栈

| 层面 | 技术 | 
|------|------|
| 后端框架 | Spring Boot 3.4 + Java 21 |
| ORM | MyBatis-Plus 3.5 |
| AI 引擎 | Spring AI Alibaba DashScope |
| LLM 模型 | 通义千问 qwen3.6-plus / qwen-max / deepseek-v3 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 6.0 |
| 实时推送 | SSE (Server-Sent Events) |
| 前端框架 | Vue 3 + Vite 8 |
| UI 库 | Ant Design Vue 4 · 暗色主题 |
| 流程画布 | Vue Flow + dagre |
| Markdown | markdown-it + highlight.js + KaTeX |

---

## 🧠 Agent 体系

### 5 个专业角色

| 角色 | 图标 | Temperature | 职责 |
|------|------|-------------|------|
| 导师 | 🧭 | 0.5 | 选题评估、大纲审阅、方向把控、最终审核 |
| 研究员 | 🔬 | 0.3 | 文献调研、信息综合、综述撰写 |
| 写作者 | ✍️ | 0.7 | 分章撰写、论证构建、语言组织 |
| 审稿人 | 📝 | 0.6 | 批判性审阅、找漏洞、迭代反馈 |
| 润色师 | ✨ | 0.4 | 语法校对、表达优化、格式规范 |

### 5 个预设写作流程

| 流程 | 步骤 | 适用场景 |
|------|------|----------|
| **标准流程** | 选题→调研→大纲→写作→审稿→润色→终审 | 通用论文 |
| **快速草稿** | 调研→大纲→写作→润色→终审 | 赶时间，快速出稿 |
| **深度研究** | 选题→调研→大纲→写作→审稿×5→润色→终审 | 高质量期刊论文 |
| **纯写作** | 写作→润色→终审 | 已有素材 |
| **综述论文** | 调研→大纲→写作→润色→终审 | 文献综述 |

---

## 🔀 可视化流程画布

### 功能特性

- **拖拽编排** — 左侧面板拖拽 Agent 节点到画布，自由连线定义执行顺序
- **四向连接** — 每个节点支持上下左右 4 个方向连线
- **条件分支** — 通过/不通过双输出，支持审稿→通过到润色、不通过回退修改
- **回退循环** — 标记回退边，支持审稿→修改→再审稿的迭代流程
- **右键菜单** — 节点删除/复制/禁用，边切换类型（普通/通过/不通过/回退）
- **撤销重做** — 完整历史栈，Ctrl+Z / Ctrl+Y
- **节点配置** — Agent 角色、System Prompt、模型、温度、超时、重试次数、备注
- **流程校验** — 孤立节点检测、环检测（回退边自动识别）
- **执行模拟** — 拓扑排序 + 节点染色动画，回退边触发循环模拟

### 4 种边类型

| 类型 | 样式 | 用途 |
|------|------|------|
| 普通边 | 白色虚线 | 顺序流转 |
| 条件边(通过) | ✓ 绿色 | 条件满足时的路径 |
| 条件边(不通过) | ✗ 红色 | 条件不满足时的路径 |
| 回退边(循环) | ↺ 蓝色虚线 | 回退到前面节点形成循环 |

---

## 📋 API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/login` | 用户登录 |
| `POST` | `/api/auth/register` | 用户注册 |
| `GET` | `/api/user/me` | 获取用户信息 |
| `POST` | `/api/paper/create` | 创建论文 |
| `POST` | `/api/paper/write/{id}` | 启动异步写作 |
| `POST` | `/api/paper/write/{id}/stop` | 停止写作 |
| `GET` | `/api/paper/write/{id}/stream` | SSE 实时进度 |
| `POST` | `/api/paper/write` | 同步写作（返回 VO） |
| `GET` | `/api/paper/list` | 论文列表（按用户隔离） |
| `GET` | `/api/paper/{id}` | 论文详情 |
| `GET` | `/api/paper/{id}/tasks` | 任务记录 |
| `GET` | `/api/paper/{id}/versions` | 版本历史 |
| `GET` | `/api/paper/{id}/versions/latest` | 最新版本 |
| `PUT` | `/api/paper/{id}/content` | 保存内容到指定版本 |
| `POST` | `/api/paper/{id}/versions` | 保存新版本 |
| `POST` | `/api/paper/{id}/agent-edit` | AI 辅助编辑 |
| `DELETE` | `/api/paper/{id}` | 删除论文 |
| `GET` | `/api/paper/flow/list` | 可用流程列表 |
| `GET` | `/api/agent/list` | Agent 列表 |
| `POST` | `/api/agent/{name}/chat` | Agent 测试对话 |
| `GET` | `/api/paper/health` | 健康检查 |

### SSE 事件类型

| 事件 | 说明 |
|------|------|
| `step` | Agent 步骤完成，包含角色/状态/输出 |
| `stream` | 流式 token 推送，实时显示生成文本 |
| `complete` | 全部流程完成 |
| `error` | 流程异常中断 |

---

## 🗄️ 数据库

### 核心表

| 表 | 说明 |
|------|------|
| `paper` | 论文元数据（标题/关键词/状态），不含 content |
| `paper_version` | 论文版本内容（content 唯一归属） |
| `task` | Agent 执行记录，含 version_no 关联版本 |
| `agent_message` | Agent 间通信记录 |
| `user` | 用户表（JWT 认证） |

---

## 🖥️ 前端页面

| 页面 | 路由 | 功能 |
|------|------|------|
| 登录 | `/login` | JWT 认证，粒子背景 + 光球装饰 |
| 注册 | `/register` | 用户注册 |
| 论文写作 | `/write` | 表单配置 + 流程选择 + SSE 实时进度 |
| 论文列表 | `/papers` | 表格展示、删除 |
| 论文详情 | `/paper/:id` | 三栏布局：目录 + 阅读/编辑 + 执行记录 |
| 流程画布 | `/flow` | Vue Flow 可视化 DAG 编辑器 |
| Agent 管理 | `/agents` | 角色卡片 + 测试对话 |

---

## 🚀 快速启动

### 前置条件

- JDK 21+ · Maven 3.8+ · MySQL 8.0+ · Redis 6.0+ · Node.js 20+

### 启动

```bash
# 1. 初始化数据库
mysql -u root -p < backend/sql/init.sql

# 2. 配置 application-local.yml（数据库密码、API Key）

# 3. 启动后端 :8081
cd backend && mvn spring-boot:run

# 4. 启动前端 :5173
cd frontend && npm install && npm run dev

# 5. 访问
open http://localhost:5173
```

---

## 📁 项目结构

```
paper-ai/
├── backend/
│   ├── sql/init.sql
│   └── src/main/java/com/paperai/
│       ├── agent/           # 5 个 Agent + AgentContext
│       ├── config/          # Security · AiConfig · JwtAuthFilter
│       ├── controller/      # PaperController · AgentController
│       ├── service/         # OrchestratorService · PaperService · StepEventPublisher
│       ├── model/
│       │   ├── entity/      # Paper · PaperVersion · Task · User
│       │   ├── dto/         # PaperWritingRequestDTO · LoginRequest
│       │   ├── vo/          # ApiResultVO · PaperWritingVO
│       │   ├── flow/        # FlowProfile 流程定义
│       │   └── enums/       # AgentRole · TaskStatus
│       ├── mapper/          # MyBatis-Plus Mapper
│       └── common/          # Constants · BusinessException
│
└── frontend/
    └── src/
        ├── api/index.js          # Axios + 拦截器
        ├── router/index.js       # 7 条路由 + JWT 守卫
        ├── stores/               # Pinia (paper · user)
        ├── styles/global.css     # 全局暗色主题
        ├── composables/          # usePaperStepSSE · useFlowLayout
        ├── components/
        │   ├── flow/             # AgentNode · ConditionNode · LoopNode
        │   │                       FlowConfigPanel · ParticleBackground
        │   ├── MarkdownRender.vue
        │   ├── TableOfContents.vue
        │   └── GlobalParticles.vue
        └── views/
            ├── Layout.vue        # 侧边栏 + 顶栏
            ├── Login.vue         # 登录（粒子光球）
            ├── Register.vue
            ├── WritePaper.vue    # 论文写作（流程选择 + SSE）
            ├── PaperList.vue     # 论文列表
            ├── PaperDetail.vue   # 三栏详情（目录/编辑/记录）
            ├── FlowCanvas.vue    # 可视化流程画布
            └── AgentList.vue     # Agent 管理
```

---

## 🎯 开发路线

### ✅ 已完成
- [x] 5 Agent 角色 + 黑板模式 + 7 步编排引擎
- [x] REST API + SSE 实时推送
- [x] JWT 用户认证 + 论文数据隔离
- [x] 论文版本管理（content 归属 paper_version）
- [x] 执行记录按版本筛选
- [x] 5 个预设写作流程 + 下拉框选择
- [x] Vue Flow 可视化流程画布（拖拽/连线/条件/循环/校验/模拟）
- [x] 全局暗色科技风主题（紫色渐变 + 粒子 + 液态玻璃）
- [x] AI 辅助编辑（选中文本 → Agent 修改）

### 🔜 计划中
- [ ] 流程画布保存/加载（后端 CRUD）
- [ ] FlowEngine 动态执行引擎（读 DAG JSON 执行）
- [ ] 论文导出（PDF / LaTeX / Word）
- [ ] RAG 知识库（上传文献作为参考）
- [ ] 团队流程模板共享
