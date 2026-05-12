-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username   VARCHAR(50)  NOT NULL UNIQUE          COMMENT '用户名',
    password   VARCHAR(255) NOT NULL                 COMMENT 'BCrypt 加密密码',
    email      VARCHAR(100)                          COMMENT '邮箱',
    avatar     VARCHAR(500)                          COMMENT '头像URL',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';
