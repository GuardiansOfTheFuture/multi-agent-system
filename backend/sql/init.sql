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
