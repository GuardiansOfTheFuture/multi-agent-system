-- =============================================
-- PaperAI 多 Agent 论文写作系统 - 数据库初始化
-- =============================================

CREATE DATABASE IF NOT EXISTS paper_ai
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE paper_ai;

-- 论文表
CREATE TABLE IF NOT EXISTS paper (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '论文ID',
    title            VARCHAR(300) NOT NULL              COMMENT '论文标题',
    abstract_text    TEXT                               COMMENT '摘要',
    keywords         VARCHAR(500)                       COMMENT '关键词',
    description      TEXT                               COMMENT '研究方向描述',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/REVIEWING/COMPLETED/FAILED',
    content          LONGTEXT                           COMMENT '最终内容',
    user_id          BIGINT       NOT NULL DEFAULT 0    COMMENT '所属用户ID',
    current_version  INT          NOT NULL DEFAULT 0    COMMENT '当前最新版本号',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文';

-- 论文版本表
CREATE TABLE IF NOT EXISTS paper_version (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '版本ID',
    paper_id        BIGINT       NOT NULL              COMMENT '所属论文ID',
    version_no      INT          NOT NULL              COMMENT '版本号',
    stage           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '阶段: DRAFT/REVIEWED/POLISHED/FINAL/MANUAL_EDIT',
    summary         VARCHAR(500)                       COMMENT '版本摘要/日志',
    content         MEDIUMTEXT                         COMMENT '论文全文(Markdown)',
    word_count      INT          DEFAULT 0             COMMENT '字数统计',
    edit_type       VARCHAR(20)  DEFAULT 'MANUAL'      COMMENT '编辑类型: MANUAL/AGENT/BATCH',
    change_summary  TEXT                               COMMENT '详细修改说明（记录修改了哪些地方）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_paper_version (paper_id, version_no),
    INDEX idx_paper_stage (paper_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文版本';

-- Agent 任务表
CREATE TABLE IF NOT EXISTS task (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    paper_id     BIGINT                                COMMENT '关联论文ID',
    agent_role   VARCHAR(30)  NOT NULL              COMMENT 'Agent角色编码',
    sort_order   INT          NOT NULL DEFAULT 0    COMMENT '执行顺序',
    description  VARCHAR(500)                       COMMENT '任务描述',
    input_data   LONGTEXT                           COMMENT '任务输入',
    output_data  LONGTEXT                           COMMENT '任务输出',
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/IN_PROGRESS/COMPLETED/FAILED',
    duration_ms  BIGINT                              COMMENT '耗时(毫秒)',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    completed_at DATETIME                            COMMENT '完成时间',
    INDEX idx_paper_id (paper_id),
    INDEX idx_agent_role (agent_role),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent任务';

-- Agent 消息表
CREATE TABLE IF NOT EXISTS agent_message (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    paper_id      BIGINT                                COMMENT '关联论文ID',
    task_id       BIGINT                                COMMENT '关联任务ID',
    sender_role   VARCHAR(30)  NOT NULL              COMMENT '发送者角色',
    receiver_role VARCHAR(30)                         COMMENT '接收者角色(null=广播)',
    message_type  VARCHAR(30)  NOT NULL              COMMENT '消息类型: COORDINATION/REVIEW_COMMENT/TASK_RESULT',
    content       LONGTEXT     NOT NULL              COMMENT '消息内容',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_paper_id (paper_id),
    INDEX idx_sender_role (sender_role),
    INDEX idx_message_type (message_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息';

-- =============================================
-- 增量变更 (已部署环境的升级语句)
-- =============================================

-- 2026-05-13: task 表增加版本号字段
ALTER TABLE task ADD COLUMN version_no INT NOT NULL DEFAULT 0 COMMENT '关联版本号';

-- 2026-05-13: paper 表删除 content 列（content 改由 paper_version 表管理）
ALTER TABLE paper DROP COLUMN content;

-- 2026-05-14: 流程定义表
CREATE TABLE IF NOT EXISTS flow_definition (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '流程ID',
    user_id     BIGINT       NOT NULL                                COMMENT '所属用户',
    name        VARCHAR(200) NOT NULL                                COMMENT '流程名称',
    description VARCHAR(500)                                         COMMENT '流程描述',
    category    VARCHAR(50)  NOT NULL DEFAULT 'custom'               COMMENT 'preset/custom/template',
    graph_data  JSON         NOT NULL                                COMMENT '完整节点+边 JSON',
    is_template TINYINT      NOT NULL DEFAULT 0                      COMMENT '是否为模板',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义表';

-- =============================================
-- 预置流程数据（含判断分支 + 循环回退）
-- =============================================

-- 流程1: 审稿条件分支 — 审稿人判断通过/不通过，分别走向润色/修改
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template) VALUES
(0, '审稿条件分支流程', '审稿人判断论文是否通过，通过→润色定稿，不通过→返回写作者修改后再审', 'preset', '{
  "nodes": [
    {"id":"n0","type":"agent","position":{"x":280,"y":40},"data":{"agentRole":"SUPERVISOR","label":"🧭 选题评估","roleName":"导师","stepIndex":1,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.7,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n1","type":"agent","position":{"x":280,"y":150},"data":{"agentRole":"RESEARCHER","label":"🔬 文献调研","roleName":"研究员","stepIndex":2,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.7,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n2","type":"agent","position":{"x":280,"y":260},"data":{"agentRole":"WRITER","label":"✍️ 撰写全文","roleName":"写作者","stepIndex":3,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.7,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n3","type":"agent","position":{"x":280,"y":370},"data":{"agentRole":"REVIEWER","label":"📝 审稿判断","roleName":"审稿人","stepIndex":4,"config":{"systemPrompt":"严格审阅论文，判断是否存在严重问题","model":"qwen-max","temperature":0.6,"timeout":120,"retryCount":2,"notes":"输出包含严重问题则走不通过分支"},"status":"pending"}},
    {"id":"n4","type":"condition","position":{"x":280,"y":480},"data":{"label":"条件分支","stepIndex":5,"config":{"condition":"output.contains(''严重问题'')","notes":"通过→润色  不通过→修改"},"status":"pending"}},
    {"id":"n5","type":"agent","position":{"x":100,"y":590},"data":{"agentRole":"WRITER","label":"✍️ 修改论文","roleName":"写作者","stepIndex":6,"config":{"systemPrompt":"根据审稿意见逐条修改论文","model":"qwen-max","temperature":0.7,"timeout":120,"retryCount":2,"notes":"修改后回到审稿人"},"status":"pending"}},
    {"id":"n6","type":"agent","position":{"x":460,"y":590},"data":{"agentRole":"POLISHER","label":"✨ 润色定稿","roleName":"润色师","stepIndex":7,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.4,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n7","type":"agent","position":{"x":460,"y":700},"data":{"agentRole":"SUPERVISOR","label":"✅ 最终审核","roleName":"导师","stepIndex":8,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.5,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}}
  ],
  "edges": [
    {"id":"e0-1","source":"n0","target":"n1","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e1-2","source":"n1","target":"n2","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e2-3","source":"n2","target":"n3","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e3-4","source":"n3","target":"n4","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e4-5","source":"n4","target":"n5","sourceHandle":"pass","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"#ff4d4f","strokeWidth":2.5},"data":{"label":"✗ 不通过","conditionType":"failure"}},
    {"id":"e4-6","source":"n4","target":"n6","sourceHandle":"right","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"#52c41a","strokeWidth":2.5},"data":{"label":"✓ 通过","conditionType":"success"}},
    {"id":"e5-3","source":"n5","target":"n3","sourceHandle":"right","targetHandle":"right","type":"default","animated":false,"style":{"stroke":"#85a5ff","strokeWidth":2.5,"strokeDasharray":"8 5"},"data":{"label":"↺ 回退","conditionType":"loop"}},
    {"id":"e6-7","source":"n6","target":"n7","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}}
  ]
}', 1);

-- 流程2: 审稿循环流程 — 审稿→修改→再审稿，最多3轮
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template) VALUES
(0, '审稿循环迭代流程', '审稿→修改→再审稿循环，最多3轮后强制进入润色', 'preset', '{
  "nodes": [
    {"id":"n0","type":"agent","position":{"x":280,"y":40},"data":{"agentRole":"RESEARCHER","label":"🔬 文献调研","roleName":"研究员","stepIndex":1,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.3,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n1","type":"agent","position":{"x":280,"y":150},"data":{"agentRole":"WRITER","label":"✍️ 撰写初稿","roleName":"写作者","stepIndex":2,"config":{"systemPrompt":"撰写完整论文初稿，注意论证逻辑","model":"qwen-max","temperature":0.7,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n2","type":"agent","position":{"x":280,"y":260},"data":{"agentRole":"REVIEWER","label":"📝 审稿","roleName":"审稿人","stepIndex":3,"config":{"systemPrompt":"批判性审阅，找出所有问题","model":"qwen-max","temperature":0.6,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n3","type":"loop","position":{"x":280,"y":370},"data":{"label":"审稿循环","stepIndex":4,"config":{"maxIterations":3,"notes":"最多3轮审稿迭代"},"status":"pending"}},
    {"id":"n4","type":"agent","position":{"x":100,"y":480},"data":{"agentRole":"WRITER","label":"✍️ 根据意见修改","roleName":"写作者","stepIndex":5,"config":{"systemPrompt":"逐条根据审稿意见修改论文","model":"qwen-max","temperature":0.7,"timeout":120,"retryCount":2,"notes":"修改完成后回到审稿人"},"status":"pending"}},
    {"id":"n5","type":"agent","position":{"x":460,"y":480},"data":{"agentRole":"POLISHER","label":"✨ 润色定稿","roleName":"润色师","stepIndex":6,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.4,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n6","type":"agent","position":{"x":460,"y":590},"data":{"agentRole":"SUPERVISOR","label":"✅ 最终审核","roleName":"导师","stepIndex":7,"config":{"systemPrompt":"","model":"qwen-max","temperature":0.5,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}}
  ],
  "edges": [
    {"id":"e0-1","source":"n0","target":"n1","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e1-2","source":"n1","target":"n2","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e2-3","source":"n2","target":"n3","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e3-4","source":"n3","target":"n4","sourceHandle":"back","targetHandle":"top","type":"default","animated":false,"style":{"stroke":"#85a5ff","strokeWidth":2.5,"strokeDasharray":"8 5"},"data":{"label":"↺ 回退","conditionType":"loop"}},
    {"id":"e3-5","source":"n3","target":"n5","sourceHandle":"next","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"#52c41a","strokeWidth":2.5},"data":{"label":"→ 继续","conditionType":"success"}},
    {"id":"e4-2","source":"n4","target":"n2","sourceHandle":"right","targetHandle":"right","type":"default","animated":false,"style":{"stroke":"#85a5ff","strokeWidth":2.5,"strokeDasharray":"8 5"},"data":{"label":"↺ 再审","conditionType":"loop"}},
    {"id":"e5-6","source":"n5","target":"n6","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}}
  ]
}', 1);

-- 流程3: 复杂学术投稿流程 — 选题评估 + 分支(可行/重选) + 写作 + 审稿循环 + 润色
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template) VALUES
(0, '学术期刊投稿完整流程', '选题评估分叉(不可行→重选)、写作→审稿循环、润色→终审，覆盖完整投稿流程', 'preset', '{
  "nodes": [
    {"id":"n0","type":"agent","position":{"x":280,"y":40},"data":{"agentRole":"SUPERVISOR","label":"🧭 选题评估","roleName":"导师","stepIndex":1,"config":{"systemPrompt":"评估选题可行性和创新性","model":"qwen-max","temperature":0.5,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n1","type":"condition","position":{"x":280,"y":150},"data":{"label":"选题可行?","stepIndex":2,"config":{"condition":"output.contains(''不可行'')","notes":"可行性判断"},"status":"pending"}},
    {"id":"n2","type":"agent","position":{"x":100,"y":260},"data":{"agentRole":"SUPERVISOR","label":"🧭 重新选题","roleName":"导师","stepIndex":3,"config":{"systemPrompt":"根据反馈重新选择论文题目","model":"qwen-max","temperature":0.5,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n3","type":"agent","position":{"x":460,"y":260},"data":{"agentRole":"RESEARCHER","label":"🔬 深度文献调研","roleName":"研究员","stepIndex":4,"config":{"systemPrompt":"针对可行选题做全面文献调研","model":"qwen-max","temperature":0.3,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n4","type":"agent","position":{"x":460,"y":370},"data":{"agentRole":"WRITER","label":"✍️ 撰写论文","roleName":"写作者","stepIndex":5,"config":{"systemPrompt":"按照期刊格式撰写完整论文","model":"qwen-max","temperature":0.7,"timeout":180,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n5","type":"agent","position":{"x":460,"y":480},"data":{"agentRole":"REVIEWER","label":"📝 同行评审","roleName":"审稿人","stepIndex":6,"config":{"systemPrompt":"模拟期刊同行评审，给出详细审稿意见","model":"qwen-max","temperature":0.6,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n6","type":"loop","position":{"x":460,"y":590},"data":{"label":"评审循环","stepIndex":7,"config":{"maxIterations":2,"notes":"最多2轮修改+再审"},"status":"pending"}},
    {"id":"n7","type":"agent","position":{"x":280,"y":700},"data":{"agentRole":"WRITER","label":"✍️ 修改并回复审稿意见","roleName":"写作者","stepIndex":8,"config":{"systemPrompt":"根据审稿意见逐条修改并撰写回复信","model":"qwen-max","temperature":0.7,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n8","type":"agent","position":{"x":670,"y":700},"data":{"agentRole":"POLISHER","label":"✨ 润色与格式检查","roleName":"润色师","stepIndex":9,"config":{"systemPrompt":"按照期刊模板格式化论文，检查参考文献","model":"qwen-max","temperature":0.4,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}},
    {"id":"n9","type":"agent","position":{"x":670,"y":810},"data":{"agentRole":"SUPERVISOR","label":"✅ 最终审查与投稿建议","roleName":"导师","stepIndex":10,"config":{"systemPrompt":"最终审查论文质量，给出投稿建议","model":"qwen-max","temperature":0.5,"timeout":120,"retryCount":2,"notes":""},"status":"pending"}}
  ],
  "edges": [
    {"id":"e0-1","source":"n0","target":"n1","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e1-2","source":"n1","target":"n2","sourceHandle":"fail","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"#ff4d4f","strokeWidth":2.5},"data":{"label":"✗ 不可行","conditionType":"failure"}},
    {"id":"e1-3","source":"n1","target":"n3","sourceHandle":"pass","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"#52c41a","strokeWidth":2.5},"data":{"label":"✓ 可行","conditionType":"success"}},
    {"id":"e2-0","source":"n2","target":"n0","sourceHandle":"right","targetHandle":"right","type":"default","animated":false,"style":{"stroke":"#85a5ff","strokeWidth":2.5,"strokeDasharray":"8 5"},"data":{"label":"↺ 重新评估","conditionType":"loop"}},
    {"id":"e3-4","source":"n3","target":"n4","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e4-5","source":"n4","target":"n5","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e5-6","source":"n5","target":"n6","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}},
    {"id":"e6-7","source":"n6","target":"n7","sourceHandle":"back","targetHandle":"top","type":"default","animated":false,"style":{"stroke":"#85a5ff","strokeWidth":2.5,"strokeDasharray":"8 5"},"data":{"label":"↺ 需修改","conditionType":"loop"}},
    {"id":"e6-8","source":"n6","target":"n8","sourceHandle":"next","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"#52c41a","strokeWidth":2.5},"data":{"label":"✓ 通过","conditionType":"success"}},
    {"id":"e7-5","source":"n7","target":"n5","sourceHandle":"right","targetHandle":"right","type":"default","animated":false,"style":{"stroke":"#85a5ff","strokeWidth":2.5,"strokeDasharray":"8 5"},"data":{"label":"↺ 再审","conditionType":"loop"}},
    {"id":"e8-9","source":"n8","target":"n9","sourceHandle":"bottom","targetHandle":"top","type":"smoothstep","animated":true,"style":{"stroke":"rgba(255,255,255,0.18)","strokeWidth":1.5},"data":{"label":"","conditionType":"normal"}}
  ]
}', 1);


-- 2026-05-15: 知识图谱表
CREATE TABLE IF NOT EXISTS knowledge_graph (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '图谱ID',
    user_id     BIGINT       NOT NULL                                COMMENT '所属用户',
    name        VARCHAR(200) NOT NULL                                COMMENT '图谱名称',
    description VARCHAR(500)                                         COMMENT '图谱描述',
    paper_id    BIGINT                                               COMMENT '关联论文ID',
    graph_data  JSON         NOT NULL                                COMMENT '节点和边 JSON',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_kg_user_id (user_id),
    INDEX idx_kg_paper_id (paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱表';

-- 2026-05-15: 示例知识图谱数据（图神经网络领域）
INSERT INTO knowledge_graph (user_id, name, description, paper_id, graph_data) VALUES (
  1,
  '图神经网络研究图谱',
  'GNN 领域核心概念、方法、论文关系图，AI 自动抽取 + 人工整理',
  NULL,
  JSON_OBJECT(
    'nodes', JSON_ARRAY(
      JSON_OBJECT('id','n1','type','topic','position',JSON_OBJECT('x',400,'y',50),'data',JSON_OBJECT('type','topic','label','图神经网络 (GNN)','desc','Graph Neural Network — 在图结构数据上运行的神经网络总称')),
      JSON_OBJECT('id','n2','type','concept','position',JSON_OBJECT('x',200,'y',180),'data',JSON_OBJECT('type','concept','label','消息传递 (Message Passing)','desc','GNN 的核心计算范式：节点通过聚合邻居信息更新自身表示')),
      JSON_OBJECT('id','n3','type','concept','position',JSON_OBJECT('x',400,'y',160),'data',JSON_OBJECT('type','concept','label','节点嵌入 (Node Embedding)','desc','将图中节点映射到低维向量空间，保留结构信息')),
      JSON_OBJECT('id','n4','type','concept','position',JSON_OBJECT('x',600,'y',180),'data',JSON_OBJECT('type','concept','label','图卷积 (Graph Convolution)','desc','将 CNN 的卷积操作推广到非欧几里得图结构上')),
      JSON_OBJECT('id','n5','type','concept','position',JSON_OBJECT('x',300,'y',320),'data',JSON_OBJECT('type','concept','label','注意力机制 (Attention)','desc','动态学习邻居节点的重要性权重，实现自适应信息聚合')),
      JSON_OBJECT('id','n6','type','method','position',JSON_OBJECT('x',120,'y',380),'data',JSON_OBJECT('type','method','label','GCN','desc','Graph Convolutional Network — Kipf & Welling 2017，谱域图卷积的简化')),
      JSON_OBJECT('id','n7','type','method','position',JSON_OBJECT('x',330,'y',430),'data',JSON_OBJECT('type','method','label','GAT','desc','Graph Attention Network — Velickovic 2018，注意力加权的消息聚合')),
      JSON_OBJECT('id','n8','type','method','position',JSON_OBJECT('x',550,'y',400),'data',JSON_OBJECT('type','method','label','GraphSAGE','desc','Hamilton 2017，归纳式节点嵌入，采样+聚合邻居')),
      JSON_OBJECT('id','n9','type','dataset','position',JSON_OBJECT('x',700,'y',340),'data',JSON_OBJECT('type','dataset','label','Cora / CiteSeer / PubMed','desc','经典引文网络基准数据集，节点=论文，边=引用关系')),
      JSON_OBJECT('id','n10','type','finding','position',JSON_OBJECT('x',500,'y',520),'data',JSON_OBJECT('type','finding','label','深层GNN 过度平滑','desc','层数加深时节点表示趋于一致，导致性能下降 (Li 2018)')),
      JSON_OBJECT('id','n11','type','problem','position',JSON_OBJECT('x',200,'y',540),'data',JSON_OBJECT('type','problem','label','可扩展性问题','desc','大规模图（百万节点+）上 GNN 训练效率低，需采样/分布式方案')),
      JSON_OBJECT('id','n12','type','paper','position',JSON_OBJECT('x',750,'y',160),'data',JSON_OBJECT('type','paper','label','Semi-Supervised Classification with GCN (ICLR 2017)','desc','Kipf & Welling，GCN 开创性论文，提出逐层传播规则'))
    ),
    'edges', JSON_ARRAY(
      JSON_OBJECT('id','e1-2','source','n1','target','n2','data',JSON_OBJECT('label','uses','relationType','uses','desc','GNN 核心范式')),
      JSON_OBJECT('id','e1-3','source','n1','target','n3','data',JSON_OBJECT('label','uses','relationType','uses','desc','GNN 的表示学习目标')),
      JSON_OBJECT('id','e1-4','source','n1','target','n4','data',JSON_OBJECT('label','uses','relationType','uses','desc','GNN 的主要实现方式')),
      JSON_OBJECT('id','e2-5','source','n2','target','n5','data',JSON_OBJECT('label','extends','relationType','extends','desc','注意力增强消息传递')),
      JSON_OBJECT('id','e2-6','source','n2','target','n6','data',JSON_OBJECT('label','part_of','relationType','part_of','desc','GCN 使用等权消息传递')),
      JSON_OBJECT('id','e5-7','source','n5','target','n7','data',JSON_OBJECT('label','proposes','relationType','proposes','desc','GAT 引入自注意力机制')),
      JSON_OBJECT('id','e6-7','source','n6','target','n7','data',JSON_OBJECT('label','extends','relationType','extends','desc','GAT 改进 GCN 的固定权重')),
      JSON_OBJECT('id','e6-8','source','n6','target','n8','data',JSON_OBJECT('label','extends','relationType','extends','desc','GraphSAGE 改进 GCN 的直推式限制')),
      JSON_OBJECT('id','e8-9','source','n8','target','n9','data',JSON_OBJECT('label','evaluates','relationType','evaluates','desc','GraphSAGE 在引文网络上评估')),
      JSON_OBJECT('id','e7-9','source','n7','target','n9','data',JSON_OBJECT('label','evaluates','relationType','evaluates','desc','GAT 在 Cora 等数据集上验证')),
      JSON_OBJECT('id','e6-10','source','n6','target','n10','data',JSON_OBJECT('label','related_to','relationType','related_to','desc','堆叠多层GCN导致过度平滑')),
      JSON_OBJECT('id','e8-11','source','n8','target','n11','data',JSON_OBJECT('label','related_to','relationType','related_to','desc','GraphSAGE 采样方案缓解可扩展性')),
      JSON_OBJECT('id','e12-6','source','n12','target','n6','data',JSON_OBJECT('label','proposes','relationType','proposes','desc','ICLR 2017 提出 GCN')),
      JSON_OBJECT('id','e12-1','source','n12','target','n1','data',JSON_OBJECT('label','related_to','relationType','related_to','desc','GCN 奠基性论文'))
    )
  )
);
