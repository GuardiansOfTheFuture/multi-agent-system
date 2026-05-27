-- =============================================
-- PaperAI 多 Agent 论文写作系统 - 建表脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS paper_ai
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE paper_ai;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL UNIQUE             COMMENT '用户名',
    password    VARCHAR(200) NOT NULL                    COMMENT '密码(BCrypt)',
    email       VARCHAR(100)                             COMMENT '邮箱',
    avatar      VARCHAR(500)                             COMMENT '头像URL',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 2. 论文表
CREATE TABLE IF NOT EXISTS paper (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '论文ID',
    title            VARCHAR(300) NOT NULL              COMMENT '论文标题',
    abstract_text    TEXT                               COMMENT '摘要',
    keywords         VARCHAR(500)                       COMMENT '关键词',
    description      TEXT                               COMMENT '研究方向描述',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/WRITING/REVIEWING/COMPLETED/FAILED',
    user_id          BIGINT       NOT NULL DEFAULT 0    COMMENT '所属用户ID',
    current_version  INT          NOT NULL DEFAULT 0    COMMENT '当前最新版本号',
    kg_id            BIGINT                            COMMENT '关联知识图谱ID',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文';

-- 3. 论文版本表
CREATE TABLE IF NOT EXISTS paper_version (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '版本ID',
    paper_id        BIGINT       NOT NULL              COMMENT '所属论文ID',
    version_no      INT          NOT NULL              COMMENT '版本号',
    stage           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '阶段: DRAFT/REVIEWED/POLISHED/FINAL/MANUAL_EDIT',
    summary         VARCHAR(500)                       COMMENT '版本摘要/日志',
    content         MEDIUMTEXT                         COMMENT '论文全文(Markdown)',
    word_count      INT          DEFAULT 0             COMMENT '字数统计',
    edit_type       VARCHAR(20)  DEFAULT 'MANUAL'      COMMENT '编辑类型: MANUAL/AGENT/BATCH',
    change_summary  TEXT                               COMMENT '详细修改说明',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_paper_version (paper_id, version_no),
    INDEX idx_paper_stage (paper_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文版本';

-- 4. Agent 任务表
CREATE TABLE IF NOT EXISTS task (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    paper_id     BIGINT                                COMMENT '关联论文ID',
    agent_role   VARCHAR(30)  NOT NULL              COMMENT 'Agent角色编码',
    sort_order   INT          NOT NULL DEFAULT 0    COMMENT '执行顺序',
    version_no   INT          NOT NULL DEFAULT 0    COMMENT '关联版本号',
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

-- 5. Agent 消息表
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

-- 6. 流程定义表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义';

-- 7. 知识图谱表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱';

-- 8. 自定义 Agent 表
CREATE TABLE IF NOT EXISTS custom_agent (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'AgentID',
    user_id       BIGINT       NOT NULL                                COMMENT '所属用户',
    name          VARCHAR(100) NOT NULL                                COMMENT '角色名称',
    icon          VARCHAR(10)  DEFAULT '🤖'                            COMMENT '图标emoji',
    description   VARCHAR(500)                                         COMMENT '角色描述',
    system_prompt TEXT                                                COMMENT '自定义System Prompt',
    model         VARCHAR(100) NOT NULL DEFAULT 'qwen-max'             COMMENT '默认模型',
    temperature   DOUBLE       NOT NULL DEFAULT 0.7                    COMMENT '默认温度',
    enabled       TINYINT      NOT NULL DEFAULT 1                      COMMENT '是否启用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ca_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义Agent';

-- 9. 参考文献表
CREATE TABLE IF NOT EXISTS paper_reference (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '引用ID',
    paper_id    BIGINT       NOT NULL                                COMMENT '关联论文ID',
    title       VARCHAR(500)                                         COMMENT '文献标题',
    authors     VARCHAR(500)                                         COMMENT '作者',
    year        INT                                                  COMMENT '发表年份',
    journal     VARCHAR(300)                                         COMMENT '期刊/会议名',
    volume      VARCHAR(50)                                          COMMENT '卷号',
    issue       VARCHAR(50)                                          COMMENT '期号',
    pages       VARCHAR(50)                                          COMMENT '页码',
    doi         VARCHAR(200)                                         COMMENT 'DOI',
    url         VARCHAR(500)                                         COMMENT '链接',
    type        VARCHAR(30)  NOT NULL DEFAULT 'other'                COMMENT '类型: journal/conference/book/other',
    raw_text    TEXT                                                 COMMENT '原始引用文本',
    cited       TINYINT      NOT NULL DEFAULT 0                      COMMENT '是否已引用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    INDEX idx_ref_paper_id (paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参考文献';

-- 10. RAG 知识库 — 文献表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '文献ID',
    user_id      BIGINT       NOT NULL                                COMMENT '上传者',
    filename     VARCHAR(300) NOT NULL                                COMMENT '原始文件名',
    file_type    VARCHAR(20)  NOT NULL                                COMMENT 'pdf/docx/md/txt',
    title        VARCHAR(500)                                         COMMENT '文献标题',
    authors      VARCHAR(500)                                         COMMENT '作者',
    year         INT                                                  COMMENT '年份',
    scope        VARCHAR(10)  NOT NULL DEFAULT 'PRIVATE'              COMMENT 'PRIVATE/SHARED',
    status       VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED'            COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    total_chunks INT          DEFAULT 0                               COMMENT '分块数',
    total_chars  INT          DEFAULT 0                               COMMENT '总字数',
    store_path   VARCHAR(500)                                        COMMENT '向量文件路径',
    embed_dim    INT                                                 COMMENT 'embedding维度',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '上传时间',
    INDEX idx_kd_user_id (user_id),
    INDEX idx_kd_scope (scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文献';

-- 11. RAG 知识库 — 分块表
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '分块ID',
    document_id  BIGINT       NOT NULL                                COMMENT '所属文献ID',
    chunk_index  INT          NOT NULL                                COMMENT '分块序号',
    content      TEXT         NOT NULL                                COMMENT '分块文本',
    char_count   INT          DEFAULT 0                               COMMENT '字数',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    INDEX idx_kc_doc_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分块';
