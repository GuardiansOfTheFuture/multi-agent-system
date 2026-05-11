# Multi-Agent 论文写作协作系统 (PaperAI)

## 📖 项目概述

本项目是一个基于 **Java Spring Boot + Vue** 的 **Multi-Agent 论文写作协作系统**，利用多个 AI Agent 扮演论文写作中的不同角色（导师、研究员、写作者、审稿人等），**协同完成从选题到完稿的全流程**。

## 🧠 一、为什么论文写作适合 Multi-Agent？

### 1.1 论文写作的天然协作属性

一篇高质量论文的诞生，从来不是一个人的独角戏。在学术界，论文写作通常涉及多个角色：

| 传统学术角色 | 对应 AI Agent | 职责 |
|-------------|--------------|------|
| **导师/指导教授** | 👨‍🏫 **导师 Agent** | 把控研究方向，审阅大纲，给出修改意见 |
| **研究员** | 🔍 **研究员 Agent** | 文献调研，数据收集，实验设计 |
| **写作者** | ✍️ **写手 Agent** | 撰写论文各章节，组织语言 |
| **审稿人** | 🔎 **审稿人 Agent** | 批判性审阅，找漏洞，提改进意见 |
| **数据分析师** | 📊 **数据分析 Agent** | 数据处理，图表生成，统计分析 |
| **润色编辑** | ✨ **润色 Agent** | 语法校对，格式规范，引用检查 |

### 1.2 论文写作全流程

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│ 选题阶段   │ → │ 调研阶段  │ → │ 撰写阶段  │ → │ 修改阶段  │
│ · 选题分析  │   │ · 文献检索 │   │ · 大纲生成 │   │ · 审稿反馈 │
│ · 可行性评估 │   │ · 文献综述 │   │ · 章节撰写 │   │ · 润色修改 │
│ · 方向建议  │   │ · 相关工作总结│ │ · 图表插入 │   │ · 格式规范 │
└──────────┘   └──────────┘   └──────────┘   └──────────┘
```

### 1.3 Multi-Agent 协作模式（论文场景）

```
🤖 选题分析 Agent ── 并行 ──→ 🤖 可行性评估 Agent
         ↓                            ↓
         └──────── 汇总报告 ──────────┘
                      ↓
         🤖 导师 Agent（审核并给出建议）
                      ↓
         ┌──── 并行执行 ────┐
         ↓                  ↓
    🤖 文献调研 Agent   🤖 大纲生成 Agent
         ↓                  ↓
         └── 辩论 ── 🤖 审稿人 Agent ──┘
                      ↓
         🤖 写手 Agent（综合撰写）
                      ↓
         🤖 润色 Agent（最终优化）
```

---

## 🏗️ 二、项目系统架构

```
┌─────────────────────────────────────────────────┐
│                  前端 (Vue 3)                     │
│  用户界面 ─ 任务管理 ─ Agent 配置 ─ 结果展示      │
└──────────────────────┬──────────────────────────┘
                       │ REST API / WebSocket
┌──────────────────────▼──────────────────────────┐
│            后端 (Spring Boot)                    │
│  ┌──────────────┐  ┌─────────────────────────┐  │
│  │  Agent 编排层  │  │   Agent 执行引擎        │  │
│  │  · 任务分解    │  │   · Agent A (研究员)    │  │
│  │  · 策略选择    │  │   · Agent B (分析师)    │  │
│  │  · 结果聚合    │  │   · Agent C (评论员)    │  │
│  └──────────────┘  └───────────┬─────────────┘  │
│                                 │                │
│  ┌──────────────────────────────▼──────────────┐ │
│  │          LLM 适配层 (DashScope SDK)         │ │
│  │       通义千问 Qwen 系列模型调用             │ │
│  └─────────────────────────────────────────────┘ │
│                                                   │
│  ┌──────────────┐  ┌─────────────────────────┐  │
│  │   MySQL       │  │   Redis                 │  │
│  │  · 用户/角色   │  │   · 会话缓存            │  │
│  │  · 任务/结果   │  │   · Agent 状态          │  │
│  │  · Agent 配置  │  │   · 消息队列            │  │
│  └──────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 2.1 后端分层架构

| 层级 | 职责 | 技术 |
|------|------|------|
| **Controller 层** | REST API 入口 | Spring Web |
| **Service 层** | 业务逻辑编排 | Spring Service |
| **Agent 引擎层** | Agent 生命周期管理 | 自定义框架 |
| **LLM 适配层** | 与大模型通信 | DashScope SDK |
| **Repository 层** | 数据持久化 | Spring Data JPA + MyBatis |
| **Cache 层** | 缓存加速 | Spring Data Redis |

### 2.2 Agent 核心设计

```
Agent 实体模型：
┌──────────────────────────────────┐
│ Agent                            │
├──────────────────────────────────┤
│ - id: Long                      │
│ - name: String (角色名称)        │
│ - role: String (角色定义)         │
│ - model: String (LLM 模型)       │
│ - temperature: Double (温度)     │
│ - systemPrompt: String (系统提示) │
│ - tools: List<Tool> (可用工具)    │
└──────────────────────────────────┘
```

### 2.3 Agent 角色设计（论文写作场景）

| Agent 角色 | 职责 | System Prompt 方向 | Temperature |
|-----------|------|-------------------|-------------|
| 👨‍🏫 **导师 Agent** | 把控方向、审核大纲、给出修改建议 | "你是一位经验丰富的博士生导师..." | 0.5 |
| 🔍 **文献研究员** | 搜索文献、综述相关工作、提取关键观点 | "你是一名专业的学术研究员..." | 0.3 |
| ✍️ **写手 Agent** | 撰写论文章节、组织论证逻辑 | "你是一名优秀的学术写作者..." | 0.7 |
| 🔎 **审稿人 Agent** | 批判性审阅、指出逻辑漏洞和不足 | "你是一位严格的论文审稿人..." | 0.6 |
| 📊 **数据分析 Agent** | 处理数据、生成图表描述、统计分析 | "你是一名数据科学专家..." | 0.3 |
| ✨ **润色 Agent** | 语法校对、学术规范、参考文献检查 | "你是一名专业的学术编辑..." | 0.4 |

### 2.4 Agent 协作策略（论文场景）

| 策略 | 适用场景 | 描述 |
|------|---------|------|
| **选题分析** | 论文刚开始 | 多个 Agent 并行分析选题的可行性和创新性 |
| **文献综述** | 调研阶段 | 研究员 Agent 搜索文献，导师 Agent 评估质量 |
| **大纲+辩论** | 撰写前 | 写手 Agent 出大纲，审稿人 Agent 挑刺，迭代优化 |
| **分章撰写** | 写作中 | 不同 Agent 负责不同章节，并行撰写 |
| **最终审阅** | 完稿后 | 审稿人 Agent → 导师 Agent → 润色 Agent 串联审阅 |

---

## 🔄 三、论文写作执行流程

### 场景一：选题分析

```
用户输入：研究兴趣/方向
    │
    ▼
┌──────────────────────────────────────────┐
│          并行执行                          │
│                                          │
│  ┌──────────────────┐                    │
│  │ 🤖 选题分析 Agent  │ ← 分析选题创新性     │
│  │  输出：选题建议    │                     │
│  └──────────────────┘                    │
│  ┌──────────────────┐                    │
│  │ 🤖 可行性 Agent   │ ← 评估数据/资源可行性 │
│  │  输出：可行性报告  │                     │
│  └──────────────────┘                    │
│  ┌──────────────────┐                    │
│  │ 🤖 文献 Agent     │ ← 初步检索相关文献    │
│  │  输出：文献概览    │                     │
│  └──────────────────┘                    │
└──────────────────┬───────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────┐
│          汇总聚合                          │
│  👨‍🏫 导师 Agent 审核并给出综合建议          │
│  输出：✅ 最终选题方案                      │
└──────────────────────────────────────────┘
```

### 场景二：论文撰写

```
用户确认选题后
    │
    ▼
┌──────────────────────────────────────────┐
│  ✍️ 写手 Agent 生成论文大纲               │
│  👨‍🏫 导师 Agent 审核大纲                  │
│  🔎 审稿人 Agent 提出质疑                 │
│  ↻ 迭代 2-3 轮直至大纲通过                 │
└──────────────────┬───────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────┐
│          分章并行撰写                      │
│                                          │
│  ✍️ 写手A → 引言 + 相关工作               │
│  ✍️ 写手B → 方法 + 实验                   │
│  ✍️ 写手C → 结果 + 讨论 + 结论             │
│  📊 数据分析 Agent → 数据/图表描述          │
└──────────────────┬───────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────┐
│          整合与审阅                        │
│                                          │
│  🔎 审稿人 Agent → 全面审阅               │
│  👨‍🏫 导师 Agent → 最终审核               │
│  ✨ 润色 Agent → 语法/格式/引用             │
│  输出：📄 完整论文初稿                      │
└──────────────────────────────────────────┘
```

### 场景三：论文润色与修改

```
用户上传论文草稿
    │
    ▼
┌──────────────────────────────────────────┐
│          并行审阅                          │
│                                          │
│  🔎 审稿人 Agent → 内容/逻辑审阅          │
│  ✨ 润色 Agent → 语言/格式审阅            │
│  📊 数据分析 Agent → 图表/数据审阅        │
└──────────────────┬───────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────┐
│  👨‍🏫 导师 Agent 汇总意见 → 给出修改计划     │
│  ✍️ 写手 Agent 执行修改                    │
│  ↻ 迭代直至满意                            │
└──────────────────────────────────────────┘
```

---

## 🗄️ 四、数据库设计（MySQL）

### 核心表结构

```sql
-- Agent 角色表（预置6种论文写作角色）
CREATE TABLE agent_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '角色名称 e.g. 导师Agent',
    role_type VARCHAR(50) NOT NULL COMMENT '类型: MENTOR, RESEARCHER, WRITER, REVIEWER, ANALYST, POLISHER',
    role_description TEXT COMMENT '角色描述',
    system_prompt TEXT NOT NULL COMMENT '系统提示词',
    model_name VARCHAR(100) DEFAULT 'qwen-max' COMMENT 'LLM 模型',
    temperature DOUBLE DEFAULT 0.7 COMMENT '创造性温度',
    avatar VARCHAR(255) COMMENT '角色头像',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 论文任务表
CREATE TABLE paper_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '论文标题',
    research_field VARCHAR(100) COMMENT '研究领域',
    keywords VARCHAR(500) COMMENT '关键词',
    abstract_text TEXT COMMENT '摘要',
    status VARCHAR(20) DEFAULT 'IDEA' COMMENT '状态: IDEA, OUTLINING, WRITING, REVIEWING, POLISHING, COMPLETED',
    scenario VARCHAR(50) NOT NULL COMMENT '场景: TOPIC_ANALYSIS, PAPER_WRITING, PAPER_POLISH',
    user_input TEXT NOT NULL COMMENT '用户原始输入',
    final_output TEXT COMMENT '最终输出（完整论文）',
    version INT DEFAULT 1 COMMENT '版本号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Agent 执行记录表（每个Agent的一次执行）
CREATE TABLE agent_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_task_id BIGINT NOT NULL,
    agent_role_id BIGINT NOT NULL,
    round INT DEFAULT 1 COMMENT '第几轮迭代',
    input_text TEXT COMMENT '输入内容',
    output_text TEXT COMMENT 'Agent输出内容',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, RUNNING, COMPLETED, FAILED',
    execution_order INT DEFAULT 0 COMMENT '执行顺序',
    started_at DATETIME,
    completed_at DATETIME,
    FOREIGN KEY (paper_task_id) REFERENCES paper_task(id),
    FOREIGN KEY (agent_role_id) REFERENCES agent_role(id)
);

-- 对话/消息历史表
CREATE TABLE conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_task_id BIGINT,
    agent_execution_id BIGINT,
    role VARCHAR(20) NOT NULL COMMENT 'USER, AGENT, SYSTEM',
    agent_role_type VARCHAR(50) COMMENT '发送消息的Agent类型',
    content TEXT NOT NULL COMMENT '消息内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (paper_task_id) REFERENCES paper_task(id)
);

-- 论文章节表（存储最终论文的章节结构）
CREATE TABLE paper_section (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_task_id BIGINT NOT NULL,
    section_number VARCHAR(20) COMMENT '章节号 e.g. 1, 2.1',
    title VARCHAR(255) NOT NULL COMMENT '章节标题',
    content LONGTEXT COMMENT '章节内容',
    created_by VARCHAR(50) COMMENT '由哪个Agent创建',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (paper_task_id) REFERENCES paper_task(id)
);
```

---

## 🎯 五、开发路线图

### Phase 1: 基础框架搭建 🏗️
- [ ] Spring Boot 项目初始化（Java 17+, Spring Boot 3.x）
- [ ] Vue 3 + Vite + Element Plus 前端初始化
- [ ] MySQL + Redis 配置与集成
- [ ] DashScope SDK 集成

### Phase 2: Agent 核心引擎 🤖
- [ ] 6种论文 Agent 角色实体定义与初始化数据
- [ ] DashScope LLM 调用封装（同步 + 流式）
- [ ] Agent 执行引擎（接收输入 → 调用 LLM → 返回输出）
- [ ] System Prompt 模板管理

### Phase 3: 论文场景实现 📝
- [ ] **选题分析场景**：多 Agent 并行分析 + 导师汇总
- [ ] **论文撰写场景**：大纲生成 → 辩论改进 → 分章撰写 → 整合润色
- [ ] **论文润色场景**：多角度审阅 + 修改迭代
- [ ] 结果聚合器：合并多 Agent 输出为完整论文

### Phase 4: 前端界面 🎨
- [ ] Agent 角色配置管理页面
- [ ] 论文任务提交面板（选择场景 → 输入主题）
- [ ] Agent 执行过程可视化（思维流展示）
- [ ] 论文结果展示（大纲/正文/审阅意见分屏展示）

### Phase 5: 增强功能 🚀
- [ ] RAG 知识库支持（上传PDF文献作为参考）
- [ ] Agent 长期记忆（记住写作风格偏好）
- [ ] 流式输出（SSE/WebSocket 实时展示）
- [ ] 论文导出（LaTeX / Word / PDF）

---

## 💡 六、论文 Agent 关键技术概念

### 6.1 各 Agent 的 System Prompt 示例

**导师 Agent 的 System Prompt：**
```
你是一位经验丰富的博士生导师，研究方向是计算机科学。
你的职责是：
1. 评估研究选题的创新性和可行性
2. 审核论文大纲的逻辑结构
3. 给出具体、可操作的修改建议
4. 确保论文达到高水平学术要求

请用专业但温和的语气给出建议，先肯定优点，再指出不足。
```

**审稿人 Agent 的 System Prompt：**
```
你是一位严格的论文审稿人，来自顶级会议/期刊。
审稿时请从以下维度评价：
1. 创新性：这项工作有新意吗？
2. 方法论：方法设计合理吗？
3. 实验：实验充分吗？结果可信吗？
4. 写作：表达清晰吗？结构合理吗？
5. 参考文献：引用全面吗？

对每个问题给出：优点、不足、改进建议。
```

### 6.2 论文写作中的 Temperature 策略

| Agent | Temperature | 原因 |
|-------|-----------|------|
| 导师 Agent | 0.5 | 需要平衡严格和鼓励 |
| 文献研究员 | 0.3 | 需要准确，避免编造 |
| 写手 Agent | 0.7 | 需要一定的创造性 |
| 审稿人 Agent | 0.6 | 需要批判性但不偏激 |
| 数据分析 Agent | 0.3 | 需要准确和客观 |
| 润色 Agent | 0.4 | 需要规范，不宜过多创造 |

### 6.3 什么是 RAG（检索增强生成）？

RAG = Retrieval Augmented Generation

在论文场景中，RAG 的作用：
- **上传 PDF 文献** → 向量化存入知识库
- **Agent 写作时** → 自动检索相关文献引用
- **避免幻觉** → 基于真实文献生成内容

### 6.4 论文格式支持

未来支持的导出格式：
- 📄 **Markdown** — 便于在线编辑和预览
- 📑 **LaTeX** — 学术论文标准格式
- 📕 **Word (.docx)** — 通用办公格式
- 📋 **PDF** — 最终提交格式

---

## 📚 七、系统特点总结

| 特点 | 说明 |
|------|------|
| 🎯 **专注论文** | 6个专业角色覆盖论文全流程 |
| 🤖 **多 Agent 协作** | 并行分析 + 辩论改进 + 迭代优化 |
| 🔌 **DashScope 驱动** | 通义千问 Qwen 系列模型 |
| 🖥️ **可视化界面** | Vue 3 前端实时展示 Agent 思维过程 |
| 🗄️ **完整数据存储** | MySQL + Redis 持久化所有记录 |
| 📤 **多格式导出** | Markdown / LaTeX / Word / PDF |

---

> 🎯 **准备好了吗？下一步我们就开始搭建 Spring Boot 后端项目！**
