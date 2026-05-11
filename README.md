# Multi-Agent 论文写作协作系统 (PaperAI)

## 📖 项目概述

本项目是一个基于 **Java Spring Boot 3.4 + Vue 3 + Ant Design Vue** 的 **Multi-Agent 论文写作协作系统**，利用 5 个 AI Agent 扮演论文写作中的不同角色（导师、研究员、写作者、审稿人、润色师），**协同完成从选题到完稿的全流程**。

用户只需提供论文主题和方向，系统自动调度 5 个 Agent **分步协作**，最终输出一篇结构完整、内容充实的学术论文初稿。

---

## 🏗️ 一、项目架构

```
┌─────────────────────────────────────────────────────────┐
│                   前端 (Vue 3 + Vite)                    │
│   Ant Design Vue · Pinia · Vue Router · Axios · KaTeX   │
│   WebSocket (STOMP) 实时接收 Agent 步骤推送              │
└─────────────────────┬───────────────────────────────────┘
                      │ REST API  /ws (STOMP WebSocket)
┌─────────────────────▼───────────────────────────────────┐
│                   后端 (Spring Boot 3.4)                  │
│                                                          │
│  ┌──────────────┐  ┌────────────────────────────────┐   │
│  │  Controller   │  │       Service 层               │   │
│  │  REST + SSE   │  │  OrchestratorService (7步)     │   │
│  └──────┬───────┘  │  PaperService / AgentTaskService│   │
│         │          │  StepEventPublisher (STOMP)     │   │
│         ▼          └────────────┬───────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │               Agent 引擎（5 个角色）                │   │
│  │  SupervisorAgent · ResearcherAgent · WriterAgent   │   │
│  │  ReviewerAgent · PolisherAgent                    │   │
│  │  AgentContext（黑板模式共享上下文）                  │   │
│  └─────────────────────┬────────────────────────────┘   │
│                        │                                │
│  ┌─────────────────────▼────────────────────────────┐   │
│  │        Spring AI Alibaba / DashScope (Qwen)       │   │
│  │      通义千问 Qwen3.6-plus (LLM 驱动引擎)          │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────┐  ┌───────────────────────────────┐    │
│  │   MySQL 8.0  │  │  Redis + WebSocket (STOMP)    │    │
│  │  paper/task  │  │  会话缓存/状态/实时步骤推送     │    │
│  └──────────────┘  └───────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

### 1.1 技术栈

| 层面 | 技术 | 版本 |
|------|------|------|
| **后端框架** | Spring Boot | 3.4.3 |
| **JDK** | Java | 21 |
| **ORM** | MyBatis-Plus | 3.5.9 |
| **AI** | Spring AI Alibaba DashScope | 1.1.2.2 |
| **模型** | 通义千问 Qwen | qwen3.6-plus |
| **数据库** | MySQL | 8.0+ |
| **缓存** | Redis | 6.0+ |
| **实时推送** | WebSocket STOMP | spring-boot-starter-websocket |
| **构建** | Maven | 3.8+ |
| **前端框架** | Vue 3 + Vite | 8.0 |
| **UI 库** | Ant Design Vue | 4.x |
| **状态管理** | Pinia | - |
| **路由** | Vue Router | 4.x |
| **HTTP** | Axios | - |
| **公式渲染** | KaTeX | - |
| **Markdown** | markdown-it + highlight.js | - |

---

## 🧠 二、核心设计

### 2.1 5 个 Agent 角色

| 角色 | Agent 类 | Temperature | 职责 |
|------|---------|-------------|------|
| 👨‍🏫 **导师** | `SupervisorAgent` | 0.5 | 选题评估、大纲审阅、方向把控、最终审核 |
| 🔍 **研究员** | `ResearcherAgent` | 0.3 | 文献调研、信息综合、综述撰写、关键发现提炼 |
| ✍️ **写作者** | `WriterAgent` | 0.7 | 分章撰写、论证构建、语言组织 |
| 🔎 **审稿人** | `ReviewerAgent` | 0.6 | 批判性审阅、找漏洞、迭代反馈 |
| ✨ **润色师** | `PolisherAgent` | 0.4 | 语法校对、表达优化、格式规范 |

### 2.2 7 步执行流程

```
用户输入主题
    │
    ▼
┌──────────────────────────────────────┐
│  ① 选题评估 (Supervisor)              │ 选题可行性 + 方向建议
├──────────────────────────────────────┤
│  ② 文献调研 (Researcher)              │ 研究综述 + 关键发现
├──────────────────────────────────────┤
│  ③ 大纲审阅 (Supervisor)              │ 大纲合理性评估 + 建议
├──────────────────────────────────────┤
│  ④ 全文撰写 (Writer)                  │ 分章节撰写（6个默认章节）
├──────────────────────────────────────┤
│  ⑤ 审稿迭代 (Reviewer ↔ Writer)      │ 最多 3 轮，无严重问题自动终止
├──────────────────────────────────────┤
│  ⑥ 润色定稿 (Polisher)               │ 语法 + 格式 + 引用检查
├──────────────────────────────────────┤
│  ⑦ 最终审核 (Supervisor)              │ 发表结论 + 最终改进建议
└──────────────────────────────────────┘
                                        ↓
                               📄 完整论文初稿 → 写入数据库
```

### 2.3 通信模式：黑板模式

所有 Agent 通过 `AgentContext`（共享上下文）进行数据交换，而非直接通信：

```
AgentContext
├── topic / abstractText / keywords       # 论文基本信息
├── researchOutput                        # 研究综述
├── outline                               # 论文大纲
├── sections: Map<String, String>         # 各章节内容
├── reviewComments: List<String>          # 审稿意见
├── finalDraft                            # 最终定稿
├── messages: List<AgentMessage>          # Agent 通信记录
└── taskStatusMap                         # 各 Agent 执行状态
```

### 2.4 实时推送（WebSocket STOMP）

```
OrchestratorService 每完成一步
    ↓
StepEventPublisher.publishStep(paperId, step)
    ↓
SimpMessagingTemplate → STOMP /topic/paper/{id}/step
    ↓
前端 usePaperStepWebSocket hook → 实时渲染折叠面板
```

---

## 🗄️ 三、数据库设计

### 3.1 核心表

```sql
-- 论文表
CREATE TABLE paper (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(300) NOT NULL,
    abstract_text TEXT,
    keywords      VARCHAR(500),
    description   TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/COMPLETED/FAILED
    content       LONGTEXT,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Agent 任务表
CREATE TABLE task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id      BIGINT,
    agent_role    VARCHAR(30) NOT NULL,
    sort_order    INT DEFAULT 0,
    description   VARCHAR(500),
    input_data    LONGTEXT,
    output_data   LONGTEXT,
    status        VARCHAR(20) DEFAULT 'PENDING',
    duration_ms   BIGINT,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at  DATETIME
);

-- Agent 消息表
CREATE TABLE agent_message (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id      BIGINT,
    task_id       BIGINT,
    sender_role   VARCHAR(30) NOT NULL,
    receiver_role VARCHAR(30),
    message_type  VARCHAR(30) NOT NULL,
    content       LONGTEXT NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🛠️ 四、完整代码结构

```
paper-ai/
├── pom.xml                              # 父 POM
├── .gitignore                           # 含 application-local.yml
│
├── backend/
│   ├── pom.xml                          # Spring Boot 3.4.3 + MyBatis-Plus + DashScope
│   ├── sql/init.sql                     # 数据库初始化脚本
│   ├── src/main/resources/
│   │   ├── application.yml              # 公共配置（profiles.active: local）
│   │   └── application-local.yml        # 🔒 敏感配置（gitignored）
│   └── src/main/java/com/paperai/
│       ├── PaperAiApplication.java
│       ├── agent/
│       │   ├── base/BaseAgent.java      # 抽象基类
│       │   ├── AgentContext.java        # 黑板模式共享上下文
│       │   ├── SupervisorAgent.java     # 导师 Agent
│       │   ├── ResearcherAgent.java     # 研究员 Agent
│       │   ├── WriterAgent.java         # 写手 Agent
│       │   ├── ReviewerAgent.java       # 审稿人 Agent
│       │   └── PolisherAgent.java       # 润色 Agent
│       ├── config/
│       │   ├── AiConfig.java            # DashScope ChatClient
│       │   ├── WebSocketConfig.java     # STOMP 配置
│       │   └── MyBatisPlusConfig.java
│       ├── controller/
│       │   ├── PaperController.java     # 论文 API
│       │   ├── AgentController.java     # Agent 调试
│       │   └── demo/TestController.java
│       ├── service/
│       │   ├── OrchestratorService.java # 7 步编排引擎
│       │   ├── StepEventPublisher.java  # WebSocket 推送
│       │   ├── PaperService.java
│       │   └── AgentTaskService.java
│       ├── model/
│       │   ├── entity/ (Paper, Task, AgentMessage)
│       │   ├── dto/ (PaperWritingRequestDTO, ResearchRequestDTO)
│       │   ├── vo/ (PaperWritingVO, ResearchResultVO...)
│       │   └── enums/ (AgentRole, TaskStatus, AgentMessageType)
│       ├── mapper/ (PaperMapper, TaskMapper, AgentMessageMapper)
│       ├── handler/ (GlobalExceptionHandler, MyMetaObjectHandler)
│       ├── common/ (Result, ResultCode, Constants, BusinessException)
│       ├── utils/ (MarkdownUtils, TextUtils)
│       └── advisor/ (LoggerAdvisor)
│
└── frontend/
    ├── vite.config.js                   # Vite + Vue + 代理配置
    ├── index.html
    └── src/
        ├── main.js                      # 入口：Vue + Pinia + Router + Antd
        ├── App.vue
        ├── api/index.js                 # Axios 封装 + SSE stream
        ├── router/index.js              # 4 条路由
        ├── stores/paper.js              # Pinia 状态
        ├── styles/global.css
        ├── components/
        │   └── MarkdownRender.vue       # Markdown + KaTeX 渲染
        ├── composables/
        │   └── usePaperStepWebSocket.js # STOMP WebSocket hook
        └── views/
            ├── Layout.vue               # 侧边栏 + 顶栏布局
            ├── WritePaper.vue           # 📝 论文写作（表单 + 实时步骤面板）
            ├── PaperList.vue            # 📚 论文列表
            ├── PaperDetail.vue          # 📄 论文详情（Markdown 渲染）
            └── AgentList.vue            # 🤖 Agent 管理
```

---

## 📋 五、REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/paper/write` | 完整论文写作（同步返回 VO） |
| `POST` | `/api/paper/research` | 单步文献调研 |
| `GET` | `/api/paper/list` | 论文列表 |
| `GET` | `/api/paper/{id}` | 论文详情 |
| `GET` | `/api/paper/{id}/tasks` | 任务记录 |
| `DELETE` | `/api/paper/{id}` | 删除论文 |
| `POST` | `/api/agent/{name}/chat` | 单 Agent 调试 |
| `GET` | `/api/agent/list` | Agent 列表 |
| `GET` | `/api/paper/health` | 健康检查 |

## 🌐 WebSocket 端点

| 订阅路径 | 事件 | 说明 |
|----------|------|------|
| `/topic/paper/{id}/step` | `StepRecordVO` | Agent 每步完成后推送 |
| `/topic/paper/{id}/complete` | `{status, paperId}` | 全部流程完成 |
| `/topic/paper/{id}/error` | `{status, error}` | 流程异常中断 |

| 连接端点 | 说明 |
|----------|------|
| `ws://localhost:8081/ws` | STOMP WebSocket 连接 |

---

## 🖥️ 六、前端页面

| 页面 | 路由 | 功能 |
|------|------|------|
| **论文写作** | `/write` | 📝 提交主题 → 实时步骤面板（WebSocket 推送） |
| **论文列表** | `/papers` | 📚 表格展示、刷新、删除 |
| **论文详情** | `/paper/:id` | 📄 Markdown 渲染 + 公式 KaTeX + 代码高亮 + 章节拆分 |
| **Agent 管理** | `/agents` | 🤖 角色卡片 + Temperature 展示 + 测试对话 |

---

## 🚀 七、快速启动

### 前置条件
- JDK 21+
- Maven 3.8+
- MySQL 8.0+（含 `paper_ai` 数据库）
- Redis 6.0+
- Node.js 20+

### 启动步骤

```bash
# 1. 初始化数据库
mysql -u root -p < backend/sql/init.sql

# 2. 配置本地敏感信息
cp backend/src/main/resources/application-local.yml.example backend/src/main/resources/application-local.yml
# 编辑 application-local.yml 填入数据库密码和 API-Key

# 3. 启动后端（端口 8081）
cd backend
mvn spring-boot:run

# 4. 启动前端（端口 5173）
cd frontend
npm install
npm run dev

# 5. 打开浏览器访问
http://localhost:5173
```

### 启动后验证

```bash
# 后端健康检查
curl http://localhost:8081/api/paper/health

# 查看 Agent 列表
curl http://localhost:8081/api/agent/list
```

---

## 🎯 八、开发路线图

### ✅ Phase 1 — 后端核心框架（已完成）
- [x] Spring Boot 3.4.3 + Java 21 初始化
- [x] MyBatis-Plus + MySQL 集成
- [x] Spring AI Alibaba DashScope (Qwen) 集成
- [x] 5 个 Agent 角色 + System Prompt
- [x] AgentContext 黑板模式共享上下文
- [x] 7 步编排引擎 OrchestratorService
- [x] 完整 REST API
- [x] Redis + WebSocket STOMP 配置
- [x] 数据库初始化脚本
- [x] 全局异常处理 + 统一返回
- [x] AI 请求日志 Advisor

### ✅ Phase 2 — 前端开发（已完成）
- [x] Vue 3 + Vite + Ant Design Vue 初始化
- [x] Pinia 状态管理 + Vue Router
- [x] 论文写作页面（表单 + 实时步骤面板）
- [x] 论文列表 + 论文详情（Markdown + KaTeX）
- [x] Agent 管理页面
- [x] WebSocket STOMP 实时推送
- [x] 刷新恢复状态（localStorage 持久化）
- [x] 公式保护渲染（$$ / $ / \[ / \( 完整支持）

### 🔜 Phase 3 — 功能增强
- [ ] SSE 单步流式输出（Agent 逐字输出）
- [ ] 多轮对话式论文修改
- [ ] 分页查询 + 搜索筛选
- [ ] Agent 消息持久化到 agent_message 表
- [ ] 论文导出（LaTeX / Word / PDF）

### 🔜 Phase 4 — 高级功能
- [ ] RAG 知识库（上传 PDF 文献作为 Agent 参考）
- [ ] Agent 长期记忆（写作风格偏好）
- [ ] 多模型切换配置
- [ ] 用户认证系统

---

## 📝 九、配置说明

### 配置拆分

| 文件 | 内容 | Git 提交 |
|------|------|----------|
| `application.yml` | 公共配置（端口、时区、MyBatis-Plus） | ✅ 提交 |
| `application-local.yml` | 敏感信息（DB密码、API-Key） | ❌ gitignored |

启动时 Spring 自动合并两个配置文件：
```bash
# application.yml 中已配置 profiles.active: local
# 自动加载 application-local.yml
mvn spring-boot:run
```

### environment.yml（环境变量）

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key |

---

## 💡 十、快速接口测试

项目中附带 `backend/http-request.http`，支持 VS Code REST Client 插件一键发送请求。

```http
### 健康检查
GET http://localhost:8081/api/paper/health

### 完整论文写作
POST http://localhost:8081/api/paper/write
Content-Type: application/json

{
  "topic": "深度学习在医疗影像分割中的应用",
  "keywords": "深度学习,医疗影像,图像分割,U-Net,Transformer",
  "sections": ["摘要", "引言", "相关工作", "方法", "实验", "结论"],
  "maxReviewRounds": 3
}
```
