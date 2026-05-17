# PaperAI — Multi-Agent 论文写作协作系统

基于 **Spring Boot 3.4 + Vue 3** 的 AI 论文写作平台。5 个专业 Agent 协作完成从选题到完稿的全流程，支持可视化流程画布、RAG 知识库增强、公式图表混排、多格式导出。

---

## 功能总览

| 模块 | 说明 |
|------|------|
| 📝 **论文写作** | 5 Agent 协作（导师→研究员→写作者→审稿人→润色师），SSE 实时进度 |
| 🔀 **流程画布** | Vue Flow 拖拽编排，条件分支、回退循环、自定义 Agent 节点 |
| 📚 **RAG 知识库** | 上传 PDF/Word/MD → Tika 解析 → DashScope Embedding → 向量检索增强写作 |
| 📄 **论文导出** | DOCX / PDF / HTML / LaTeX 一键导出 |
| 📊 **图表渲染** | Mermaid 流程图 + ECharts 数据图表，Markdown 内嵌渲染 |
| 🔧 **脚本解析器** | Mermaid / ECharts 在线编辑 + 实时预览 + PNG 导出 |
| 🤖 **自定义 Agent** | 用户自定义角色、System Prompt、模型、温度 |
| 🧠 **知识图谱** | D3 力导向图可视化 + AI 抽取 + 文件上传提取 |
| 🌓 **暗色主题** | 全局暗色科技风，紫色渐变 + 粒子背景 |

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.4 + Java 21 + MyBatis-Plus |
| AI | Spring AI Alibaba DashScope（通义千问 / DeepSeek） |
| 数据库 | MySQL 8.0 + Redis（Redisson） |
| 前端 | Vue 3 + Vite + Ant Design Vue 4 + Pinia |
| 流程画布 | Vue Flow + dagre |
| 图表 | Mermaid + ECharts + KaTeX + D3 |
| 文档解析 | Apache Tika + PDFBox + POI |
| 实时通信 | SSE (Server-Sent Events) |

---

## Agent 体系

| 角色 | 默认模型 | 职责 |
|------|---------|------|
| 🧭 导师 | qwen-max | 选题评估、大纲审阅、最终审核 |
| 🔬 研究员 | qwen-max | 文献调研、信息综合、综述撰写 |
| ✍️ 写作者 | qwen-max | 分章撰写、论证构建、图表生成 |
| 📝 审稿人 | qwen-max | 批判性审阅、评分、迭代反馈 |
| ✨ 润色师 | qwen-plus | 语法校对、表达优化、格式规范 |

支持自定义 Agent：角色名称、图标、System Prompt、模型、温度自由配置。

---

## 预设流程

| 流程 | 步骤 | 适用 |
|------|------|------|
| 标准流程 | 选题→调研→大纲→写作→审稿→润色→终审 | 通用 |
| 快速草稿 | 调研→大纲→写作→润色→终审 | 快速出稿 |
| 深度研究 | 选题→调研→大纲→写作→审稿×5→润色→终审 | 期刊论文 |
| 纯写作 | 写作→润色→终审 | 已有素材 |
| 综述论文 | 调研→大纲→写作→润色→终审 | 文献综述 |

---

## RAG 知识库

```
上传 PDF/Word/MD → TikaDocumentReader 解析 → DashScope Embedding
→ 按用户+文档独立 JSON 存储 → 写作时向量检索 Top-K → 注入上下文
```

- 个人库 + 共享库双模式
- 嵌入模型：DashScope text-embedding-v3
- 自动分块（≤2000 字/块，句号边界断）

---

## 项目结构

```
paper-ai/
├── backend/
│   ├── sql/init.sql
│   └── src/main/java/com/paperai/
│       ├── agent/           # 5 Agent + BaseAgent + AgentContext
│       ├── config/          # Security · AiConfig · JwtAuthFilter · RedissonConfig
│       ├── controller/      # Paper · Agent · Flow · Knowledge · KnowledgeGraph
│       ├── service/         # OrchestratorService · FlowEngine · KnowledgeService · ExportService
│       ├── model/
│       │   ├── entity/      # Paper · PaperVersion · Task · User · KnowledgeDocument · KnowledgeChunk
│       │   ├── dto/vo/      # 请求/响应 DTO
│       │   ├── flow/        # FlowProfile
│       │   └── enums/       # AgentRole · TaskStatus · AgentMessageType
│       ├── mapper/          # MyBatis-Plus
│       ├── common/          # Constants · BusinessException
│       └── advisor/         # LoggerAdvisor
│
└── frontend/
    └── src/
        ├── api/index.js
        ├── router/index.js
        ├── stores/          # Pinia (paper · user)
        ├── styles/global.css
        ├── components/
        │   ├── flow/        # AgentNode · ConditionNode · LoopNode · PaperNode
        │   ├── MarkdownRender.vue   # Mermaid + ECharts + KaTeX
        │   └── TableOfContents.vue
        └── views/
            ├── Layout.vue           # 侧边栏 + 顶栏
            ├── Login.vue · Register.vue
            ├── WritePaper.vue       # 论文写作 + SSE
            ├── PaperList.vue        # 论文列表
            ├── PaperDetail.vue      # 三栏详情（目录/编辑/任务记录）
            ├── FlowCanvas.vue       # 可视化流程画布
            ├── AgentList.vue        # Agent 管理 + 自定义 Agent
            ├── KnowledgeBase.vue    # RAG 知识库
            ├── KnowledgeGraph.vue   # D3 知识图谱
            └── ScriptPlayground.vue # 脚本解析器
```

---

## 数据库

| 表 | 说明 |
|------|------|
| `paper` | 论文元数据 |
| `paper_version` | 论文版本内容 |
| `task` | Agent 执行记录 |
| `user` | 用户表 |
| `flow_definition` | 自定义流程 + 模板 |
| `knowledge_graph` | 知识图谱数据 |
| `knowledge_document` | 知识库文献元数据 |
| `knowledge_chunk` | 知识库分块文本 |
| `custom_agent` | 自定义 Agent |
| `paper_reference` | 参考文献 |

---

## 快速启动

```bash
# 1. 初始化数据库
mysql -u root -p paper_ai < backend/sql/init.sql

# 2. 配置 application-local.yml

# 3. 后端 :8081
cd backend && mvn spring-boot:run

# 4. 前端 :5173
cd frontend && npm install && npm run dev
```
