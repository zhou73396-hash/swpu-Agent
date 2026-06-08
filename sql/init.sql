-- ============================================================
-- swpu-agent 完整数据库初始化脚本
--
-- 一键创建 agent 数据库及所有表（Python Agent + Java Backend 共用）
-- 用法: mysql -u root -proot < sql/init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS agent
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent;

-- ============================================================
-- 1. user_info — 用户信息表（Python Agent 权限中间件依赖）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_info (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name  VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL UNIQUE,
    role       VARCHAR(50)  DEFAULT '普通员工',
    age        INT          DEFAULT NULL,
    country    VARCHAR(100) DEFAULT '中国',
    salary     DECIMAL(12,2) DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_info_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户信息表';

-- ============================================================
-- 2. users — JWT 登录账号表（Java Auth 依赖）
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    real_name     VARCHAR(100) DEFAULT NULL,
    role          ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    avatar        VARCHAR(500) DEFAULT NULL,
    refresh_token VARCHAR(128) DEFAULT NULL,
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    last_login_at DATETIME     DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_username (username),
    INDEX idx_users_refresh_token (refresh_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='JWT登录账号';

-- ============================================================
-- 3. chat_sessions — 对话会话表（Java Chat 依赖）
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    db_connection_id BIGINT DEFAULT NULL,
    title            VARCHAR(200) NOT NULL DEFAULT 'New Chat',
    status           ENUM('ACTIVE', 'ARCHIVED', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
    message_count    INT NOT NULL DEFAULT 0,
    total_tokens_used INT NOT NULL DEFAULT 0,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sessions_user_id (user_id),
    INDEX idx_sessions_status (status),
    INDEX idx_sessions_updated (user_id, updated_at DESC),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES user_info(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='对话会话';

-- ============================================================
-- 4. chat_messages — 对话消息表（Java Chat 依赖）
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_messages (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id   BIGINT NOT NULL,
    role         ENUM('USER', 'ASSISTANT', 'SYSTEM', 'TOOL') NOT NULL,
    content      TEXT NOT NULL,
    message_type ENUM('TEXT', 'SQL', 'CHART', 'THINKING', 'ERROR') NOT NULL DEFAULT 'TEXT',
    metadata     JSON DEFAULT NULL,
    token_count  INT DEFAULT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_messages_session (session_id, created_at),
    INDEX idx_messages_role (role),
    CONSTRAINT fk_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='对话消息';

-- ============================================================
-- 5. db_connections — 数据库连接配置表（Java DB 连接管理）
-- ============================================================
CREATE TABLE IF NOT EXISTS db_connections (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    name               VARCHAR(100) NOT NULL,
    db_type            ENUM('MYSQL', 'POSTGRESQL') NOT NULL DEFAULT 'MYSQL',
    host               VARCHAR(255) NOT NULL,
    port               INT NOT NULL DEFAULT 3306,
    database_name      VARCHAR(100) NOT NULL,
    username           VARCHAR(100) NOT NULL,
    encrypted_password VARCHAR(500) NOT NULL,
    is_active          TINYINT(1) NOT NULL DEFAULT 1,
    last_tested_at     DATETIME DEFAULT NULL,
    test_status        ENUM('UNTESTED', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'UNTESTED',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_connections_user (user_id),
    INDEX idx_connections_type (db_type),
    UNIQUE KEY uk_user_connection_name (user_id, name),
    CONSTRAINT fk_connections_user FOREIGN KEY (user_id) REFERENCES user_info(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='外部数据库连接配置';

-- ============================================================
-- 6. tool_invocations — Agent 工具调用日志（Java 审计）
-- ============================================================
CREATE TABLE IF NOT EXISTS tool_invocations (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id       BIGINT NOT NULL,
    tool_name        VARCHAR(50) NOT NULL,
    input_params     JSON NOT NULL,
    output_result    JSON DEFAULT NULL,
    status           ENUM('PENDING', 'RUNNING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
    execution_time_ms INT DEFAULT NULL,
    error_message    TEXT DEFAULT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_invocations_message (message_id),
    INDEX idx_invocations_tool (tool_name),
    INDEX idx_invocations_status (status),
    CONSTRAINT fk_invocations_message FOREIGN KEY (message_id) REFERENCES chat_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Agent工具调用审计日志';

-- ============================================================
-- 7. 业务数据表（Python SQL 问答 Agent 查询对象）
-- ============================================================

-- 客户表
CREATE TABLE IF NOT EXISTS customer (
    user_id           INT PRIMARY KEY,
    username          VARCHAR(100),
    registration_date DATE,
    country           VARCHAR(100),
    age               INT,
    gender            VARCHAR(10),
    total_spent       DECIMAL(12,2),
    order_count       INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='客户表';

-- 产品表
CREATE TABLE IF NOT EXISTS products (
    product_id     INT PRIMARY KEY,
    product_name   VARCHAR(200),
    category       VARCHAR(100),
    price          DECIMAL(10,2),
    stock          INT,
    sales_volume   INT,
    average_rating DECIMAL(3,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='产品表';

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    order_id       INT PRIMARY KEY,
    user_id        INT,
    order_date     DATE,
    product_id     INT,
    quantity       INT,
    total_amount   DECIMAL(12,2),
    payment_method VARCHAR(50),
    order_status   VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='订单表';

-- 客户行为表
CREATE TABLE IF NOT EXISTS customer_behavior (
    id          INT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT,
    product_id  INT,
    action      VARCHAR(50),
    action_date DATE,
    device      VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='客户行为表';

-- 销售统计表
CREATE TABLE IF NOT EXISTS sales (
    id                  INT PRIMARY KEY AUTO_INCREMENT,
    year                INT,
    total_sales         DECIMAL(14,2),
    total_orders        INT,
    total_quantity_sold INT,
    category            VARCHAR(100),
    average_order_value DECIMAL(12,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='销售统计表';

-- ============================================================
-- 测试数据
-- ============================================================

-- 测试用户（role=总经理 有全部权限，Python 权限中间件需要）
INSERT INTO user_info (id, user_name, email, role, age, country, salary) VALUES
(1, '测试管理员', 'admin@test.com', '总经理', 30, '中国', 50000)
ON DUPLICATE KEY UPDATE user_name=VALUES(user_name);

-- 测试销售数据
INSERT INTO sales (year, total_sales, total_orders, total_quantity_sold, category, average_order_value) VALUES
(2023, 1200000.00, 1500, 30000, '电子产品', 800.00),
(2023,  800000.00, 1200, 25000, '家居用品', 666.67),
(2024, 1500000.00, 1800, 35000, '电子产品', 833.33),
(2024,  900000.00, 1400, 28000, '家居用品', 642.86)
ON DUPLICATE KEY UPDATE total_sales=VALUES(total_sales);
