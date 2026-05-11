# Multi-Agent 研究分析协作系统

## 📖 项目概述

本项目是一个基于 **Java Spring Boot + Vue** 的 **Multi-Agent 协作系统**，利用多个 AI Agent 从不同角度对问题进行分析研究，并汇总生成综合性结论。

## 🧠 一、什么是 Multi-Agent 系统？

### 1.1 基本概念

**Agent（智能体）** 是一个能够感知环境、自主决策并执行行动的 AI 实体。它通常具备以下能力：
- **感知**：接收用户输入和系统状态
- **推理**：使用 LLM 进行逻辑思考和决策
- **行动**：调用工具、执行代码、返回结果
- **记忆**：存储和检索历史信息

**Multi-Agent 系统** 是多个 Agent 协同工作的系统，核心思想是 **"分工协作，集思广益"**。

### 1.2 为什么需要多个 Agent？

| 单一 Agent | Multi-Agent |
|-----------|-------------|
| 单一视角分析 | 多角度交叉验证 |
| 容易产生偏见 | 相互批评纠正 |
| 知识面有限 | 角色专业化分工 |
| 处理复杂任务能力有限 | 任务分解并行处理 |

### 1.3 常见 Multi-Agent 协作模式

```
1. 顺序模式（Pipeline）
   Agent A → Agent B → Agent C → 汇总结果

2. 并行模式（Parallel）
       ┌→ Agent A ─┐
   输入 ┤→ Agent B ├→ 汇总
       └→ Agent C ─┘

3. 辩论模式（Debate）
   Agent A ←→ Agent B ←→ Agent C
       ↓  辩论与反驳  ↓
         最终裁决者

4. 层级模式（Hierarchy）
         管理者 Agent
        /     |     \
    AgentA  AgentB  AgentC
```

---

## 🏗️ 二、本项目系统架构

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

### 2.3 Agent 角色设计（研究分析场景）

| Agent 角色 | 职责 | 系统提示词方向 |
|-----------|------|---------------|
| **研究员 (Researcher)** | 收集信息、查找事实 | "你是一个专业研究员，擅长收集和分析信息" |
| **分析师 (Analyst)** | 深度分析、发现模式 | "你是一个资深分析师，善于发现数据中的模式" |
| **评论员 (Critic)** | 批判性评估、找出漏洞 | "你是一个批判性思考者，擅长找出论证中的漏洞" |
| **总结者 (Summarizer)** | 汇总各方观点、生成结论 | "你是一个总结专家，善于整合不同观点" |

---

## 🔄 三、任务执行流程

```
用户提交问题
    │
    ▼
┌─────────────┐
│ 任务分解器    │ ← 将复杂问题拆解为子任务
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 策略选择器    │ ← 选择协作模式（并行/顺序/辩论）
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│        Agent 并行执行                │
│                                     │
│  ┌──────────┐  ┌──────────┐         │
│  │ 研究员    │  │ 分析师    │         │
│  │ (收集信息) │  │ (深度分析) │         │
│  └─────┬────┘  └─────┬────┘         │
│        │              │              │
│  ┌─────▼────┐  ┌─────▼────┐         │
│  │ 评论员    │  │ 总结者    │         │
│  │ (评估反馈) │  │ (汇总结论) │         │
│  └──────────┘  └──────────┘         │
└─────────────────────────────────────┘
       │
       ▼
┌─────────────┐
│ 结果聚合器    │ ← 合并各 Agent 输出
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 最终报告     │ ← 呈现给用户
└─────────────┘
```

---

## 🗄️ 四、数据库设计（MySQL）

### 核心表结构

```sql
-- Agent 角色表
CREATE TABLE agent_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    role_description TEXT,
    system_prompt TEXT NOT NULL,
    model_name VARCHAR(100),
    temperature DOUBLE DEFAULT 0.7,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 任务表
CREATE TABLE task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, RUNNING, COMPLETED, FAILED
    strategy VARCHAR(50) DEFAULT 'PARALLEL', -- PARALLEL, SEQUENTIAL, DEBATE
    user_input TEXT,
    final_result TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Agent 执行记录表
CREATE TABLE agent_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    agent_role_id BIGINT NOT NULL,
    input_text TEXT,
    output_text TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    execution_order INT DEFAULT 0,
    started_at DATETIME,
    completed_at DATETIME,
    FOREIGN KEY (task_id) REFERENCES task(id),
    FOREIGN KEY (agent_role_id) REFERENCES agent_role(id)
);

-- 对话历史表
CREATE TABLE conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    agent_execution_id BIGINT,
    role VARCHAR(20) NOT NULL,  -- USER, AGENT, SYSTEM
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES task(id)
);
```

---

## 🎯 五、开发路线图

### Phase 1: 基础框架搭建 ✅
- [ ] Spring Boot 项目初始化
- [ ] Vue 3 前端项目初始化
- [ ] MySQL + Redis 配置
- [ ] DashScope SDK 集成

### Phase 2: Agent 核心引擎
- [ ] Agent 角色管理 CRUD
- [ ] LLM 调用封装
- [ ] 单一 Agent 对话功能
- [ ] Agent 提示词模板管理

### Phase 3: Multi-Agent 协作
- [ ] 任务分解器
- [ ] 并行 Agent 执行
- [ ] 结果聚合器
- [ ] 辩论模式实现

### Phase 4: 前端界面
- [ ] Agent 配置管理界面
- [ ] 任务提交与监控面板
- [ ] 多 Agent 执行可视化
- [ ] 结果展示与对比

### Phase 5: 增强功能
- [ ] 知识库支持（RAG）
- [ ] Agent 记忆持久化
- [ ] 工具调用（Function Calling）
- [ ] 流式输出（SSE/WebSocket）

---

## 💡 六、关键技术概念解释

### 6.1 什么是 Agent 的 System Prompt？

System Prompt 是给 AI 模型设定的 **"角色身份和行为规则"**。它定义了 Agent 的：
- **身份**：你是什么角色？
- **目标**：你要完成什么任务？
- **约束**：你需要注意什么？
- **输出格式**：你如何呈现结果？

### 6.2 什么是 Temperature？

Temperature 控制 AI 输出的 **随机性/创造性**：
- **低 (0.1~0.3)**：输出更确定、更保守 → 适合事实性分析
- **中 (0.5~0.7)**：平衡创造性和准确性 → 适合一般任务
- **高 (0.8~1.0)**：更有创意、更多样化 → 适合头脑风暴

### 6.3 什么是 DashScope？

DashScope（阿里云灵积）是阿里云提供的大模型服务平台，可以调用通义千问（Qwen）系列模型。通过其 SDK，我们可以轻松集成 LLM 能力。

### 6.4 什么是 Agent 编排（Orchestration）？

Agent 编排是指 **管理和协调多个 Agent 的执行过程**，包括：
- 决定哪个 Agent 在什么时候执行
- 传递上下文信息
- 收集和处理各 Agent 的输出
- 处理错误和异常情况

---

## 📚 七、推荐学习资源

1. **LangChain 官方文档** - 了解 Agent 框架设计模式
2. **AutoGen (Microsoft)** - 微软的 Multi-Agent 框架
3. **CrewAI** - 多 Agent 协作框架
4. **Spring AI** - Spring 生态的 AI 集成框架
5. **阿里云 DashScope 文档** - 通义千问 API 使用指南

---

> 准备好了吗？下一步我们将从头开始搭建项目！
