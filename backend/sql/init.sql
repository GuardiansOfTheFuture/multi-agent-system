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
