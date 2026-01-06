-- V5: Create Execution Tables
-- Phase 1: Foundation - Unified execution infrastructure

-- ===================================
-- Execution History Table
-- ===================================
CREATE TABLE execution_history (
    -- Primary Key
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- Execution Identity
    execution_id VARCHAR(36) NOT NULL UNIQUE COMMENT '실행 ID (API 식별자)',
    execution_type VARCHAR(20) NOT NULL COMMENT '실행 타입 (DATASET/QUALITY/RAW_SQL)',
    resource_name VARCHAR(255) COMMENT '리소스 이름 (Dataset/Quality Spec 이름)',

    -- Execution Status
    status VARCHAR(20) NOT NULL COMMENT '실행 상태',
    started_at TIMESTAMP NOT NULL COMMENT '실행 시작 시간',
    completed_at TIMESTAMP COMMENT '실행 완료 시간',
    duration_ms BIGINT COMMENT '실행 시간 (밀리초)',

    -- Execution Context
    user_id BIGINT NOT NULL COMMENT '실행을 트리거한 사용자 ID',
    transpiled_sql TEXT NOT NULL COMMENT '실행된 SQL (Transpiled)',
    parameters JSON COMMENT '실행 파라미터 (JSON)',
    reason VARCHAR(500) COMMENT '실행 사유/설명',
    dialect VARCHAR(20) COMMENT 'SQL Dialect',

    -- Error Tracking
    error_code VARCHAR(20) COMMENT '에러 코드',
    error_message TEXT COMMENT '에러 메시지',

    -- Audit Fields (from BaseEntity)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,

    -- Indexes
    INDEX idx_execution_history_execution_id (execution_id),
    INDEX idx_execution_history_execution_type (execution_type),
    INDEX idx_execution_history_resource_name (resource_name),
    INDEX idx_execution_history_status (status),
    INDEX idx_execution_history_started_at (started_at),
    INDEX idx_execution_history_user_id (user_id),
    INDEX idx_execution_history_type_status (execution_type, status),
    INDEX idx_execution_history_resource_status (resource_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='실행 이력 테이블';

-- ===================================
-- Execution Result Table
-- ===================================
CREATE TABLE execution_result (
    -- Primary Key
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- Foreign Key (1:1 with execution_history)
    execution_id VARCHAR(36) NOT NULL UNIQUE COMMENT '실행 ID (FK to execution_history)',

    -- Result Data
    result_data JSON NOT NULL COMMENT '결과 데이터 (JSON)',
    row_count INT NOT NULL COMMENT '결과 행 수',
    schema JSON COMMENT '결과 스키마 (JSON)',

    -- Audit Fields (from BaseEntity)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,

    -- Index
    INDEX idx_execution_result_execution_id (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='실행 결과 테이블';
