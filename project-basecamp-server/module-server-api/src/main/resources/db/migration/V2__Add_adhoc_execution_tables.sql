-- Ad-Hoc Execution Tables for Run API
-- This migration creates tables for ad-hoc SQL execution tracking and user quota management

-- Ad-Hoc 쿼리 실행 기록 테이블
CREATE TABLE IF NOT EXISTS adhoc_executions (
    id VARCHAR(36) PRIMARY KEY,
    query_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    sql_query TEXT NOT NULL,
    rendered_sql TEXT NOT NULL,
    engine VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    rows_returned INT DEFAULT NULL,
    bytes_scanned BIGINT DEFAULT NULL,
    cost_usd DECIMAL(10, 6) DEFAULT NULL,
    execution_time_seconds DOUBLE DEFAULT NULL,
    result_path VARCHAR(500) DEFAULT NULL,
    error_message VARCHAR(5000) DEFAULT NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_adhoc_exec_query_id (query_id),
    INDEX idx_adhoc_exec_user_id (user_id),
    INDEX idx_adhoc_exec_status (status),
    INDEX idx_adhoc_exec_engine (engine),
    INDEX idx_adhoc_exec_created_at (created_at),
    INDEX idx_adhoc_exec_expires_at (expires_at)
);

-- 사용자별 실행 할당량 테이블
CREATE TABLE IF NOT EXISTS user_execution_quotas (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    queries_today INT NOT NULL DEFAULT 0,
    queries_this_hour INT NOT NULL DEFAULT 0,
    last_query_date DATE NOT NULL,
    last_query_hour INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    INDEX idx_user_exec_quota_user_id (user_id),
    INDEX idx_user_exec_quota_last_query_date (last_query_date)
);
