-- =============================================================================
-- DataOps Platform - Local Development Database Initialization
-- =============================================================================
-- Description: Merged and idempotent DDL script for local MySQL development
-- Usage: mysql -u root -p dataops < scripts/init-local-db.sql
-- Note: All statements are idempotent (safe to run multiple times)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Core Tables (from V1__Initial_schema.sql)
-- -----------------------------------------------------------------------------

-- User table (MySQL reserved word - use backticks)
CREATE TABLE IF NOT EXISTS `user`
(
    `id`             INT AUTO_INCREMENT PRIMARY KEY,
    `username`       VARCHAR(40) UNIQUE,
    `email`          VARCHAR(40) UNIQUE,
    `password`       VARCHAR(200) DEFAULT NULL,
    `role`           VARCHAR(40)  DEFAULT NULL,
    `enabled`        BOOLEAN      DEFAULT NULL,
    `last_active_at` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `created_at`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at`     TIMESTAMP    DEFAULT NULL,
    `created_by`     INT          DEFAULT NULL,
    `updated_by`     INT          DEFAULT NULL,
    `deleted_by`     INT          DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS user_authority
(
    id        INT AUTO_INCREMENT PRIMARY KEY,
    user_id   INT         DEFAULT NULL,
    authority VARCHAR(40) DEFAULT NULL
);

-- Index: Skip if exists (wrapped in procedure for idempotency)
DROP PROCEDURE IF EXISTS create_index_if_not_exists;
DELIMITER //
CREATE PROCEDURE create_index_if_not_exists()
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    -- user_authority_unique_idx
    SELECT COUNT(*) INTO index_exists FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'user_authority' AND index_name = 'user_authority_unique_idx';
    IF index_exists = 0 THEN
        CREATE UNIQUE INDEX user_authority_unique_idx ON user_authority (user_id, authority);
    END IF;
END //
DELIMITER ;
CALL create_index_if_not_exists();
DROP PROCEDURE IF EXISTS create_index_if_not_exists;

-- Seed user data
INSERT IGNORE INTO `user` (id, username, email, password, role, enabled)
VALUES (1, '1ambda', '1ambda@github.com', NULL, 'ADMIN', 1);

INSERT IGNORE INTO user_authority (id, user_id, authority)
VALUES (1, 1, 'PERMISSION_A'),
       (2, 1, 'PERMISSION_B');

-- Audit tables
CREATE TABLE IF NOT EXISTS audit_resource_history
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT       DEFAULT NULL,
    resource_type    VARCHAR(40) NOT NULL,
    resource_id      INT         NOT NULL,
    resource_content JSON        NULL,
    action           VARCHAR(40) NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_access_history
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT       DEFAULT NULL,
    access_type    VARCHAR(40) NOT NULL,
    access_content JSON        NULL,
    action         VARCHAR(40) NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Resource table
CREATE TABLE IF NOT EXISTS resource
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT       DEFAULT NULL,
    name       VARCHAR(40) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL,
    created_by INT       DEFAULT NULL,
    updated_by INT       DEFAULT NULL,
    deleted_by INT       DEFAULT NULL
);

-- Pipeline table
CREATE TABLE IF NOT EXISTS pipelines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    owner VARCHAR(50) NOT NULL,
    schedule_expression VARCHAR(200),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_pipeline_owner (owner),
    INDEX idx_pipeline_status (status),
    INDEX idx_pipeline_active (is_active),
    INDEX idx_pipeline_schedule (schedule_expression)
);

-- Jobs table
CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    execution_order INT NOT NULL DEFAULT 0,
    config JSON,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    error_message TEXT,
    pipeline_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_job_pipeline (pipeline_id),
    INDEX idx_job_status (status),
    INDEX idx_job_type (type),
    INDEX idx_job_execution_order (execution_order)
);

-- Datasets table
CREATE TABLE IF NOT EXISTS datasets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    type VARCHAR(50) NOT NULL,
    format VARCHAR(50) NOT NULL,
    location VARCHAR(500) NOT NULL,
    schema_definition JSON,
    connection_info JSON,
    tags JSON,
    owner VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    INDEX idx_dataset_owner (owner),
    INDEX idx_dataset_type (type),
    INDEX idx_dataset_format (format),
    INDEX idx_dataset_active (is_active)
);

-- Team table
CREATE TABLE IF NOT EXISTS team (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '팀 식별자 (lowercase, hyphenated)',
    display_name VARCHAR(200) NOT NULL COMMENT '팀 표시명',
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    deleted_by BIGINT DEFAULT NULL,
    INDEX idx_team_name (name),
    INDEX idx_team_deleted_at (deleted_at)
);

-- Worksheet folder table
CREATE TABLE IF NOT EXISTS worksheet_folder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL COMMENT 'FK to team',
    name VARCHAR(100) NOT NULL COMMENT '폴더명 (팀 내 unique)',
    description VARCHAR(500),
    display_order INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    deleted_by BIGINT DEFAULT NULL,
    INDEX idx_worksheet_folder_team_id (team_id),
    INDEX idx_worksheet_folder_deleted_at (deleted_at),
    UNIQUE KEY uk_worksheet_folder_name_team (name, team_id)
);

-- SQL Worksheet table
CREATE TABLE IF NOT EXISTS sql_worksheet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    folder_id BIGINT NOT NULL COMMENT 'FK to worksheet_folder',
    name VARCHAR(200) NOT NULL COMMENT 'Worksheet 이름',
    description VARCHAR(1000),
    sql_text TEXT NOT NULL COMMENT 'SQL 쿼리 내용',
    dialect VARCHAR(20) NOT NULL DEFAULT 'BIGQUERY' COMMENT 'SQL 방언',
    run_count INT NOT NULL DEFAULT 0 COMMENT '실행 횟수',
    last_run_at TIMESTAMP NULL COMMENT '마지막 실행 시간',
    is_starred BOOLEAN NOT NULL DEFAULT FALSE COMMENT '즐겨찾기 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    deleted_by BIGINT DEFAULT NULL,
    INDEX idx_sql_worksheet_folder_id (folder_id),
    INDEX idx_sql_worksheet_name (name),
    INDEX idx_sql_worksheet_starred (is_starred),
    INDEX idx_sql_worksheet_deleted_at (deleted_at)
);

-- -----------------------------------------------------------------------------
-- 2. Ad-Hoc Execution Tables (from V2__Add_adhoc_execution_tables.sql)
-- -----------------------------------------------------------------------------

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

-- -----------------------------------------------------------------------------
-- 3. Lineage Tables (from V3__Add_lineage_tables.sql)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lineage_nodes (
    name VARCHAR(255) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    owner VARCHAR(100) DEFAULT NULL,
    team VARCHAR(100) DEFAULT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lineage_node_type (type),
    INDEX idx_lineage_node_owner (owner),
    INDEX idx_lineage_node_team (team)
);

CREATE TABLE IF NOT EXISTS lineage_node_tags (
    lineage_node_name VARCHAR(255) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (lineage_node_name, tag),
    INDEX idx_lineage_node_tags_tag (tag)
);

CREATE TABLE IF NOT EXISTS lineage_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    edge_type VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    description VARCHAR(1000) DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    deleted_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    UNIQUE KEY idx_lineage_edge_source_target (source, target),
    INDEX idx_lineage_edge_source (source),
    INDEX idx_lineage_edge_target (target),
    INDEX idx_lineage_edge_type (edge_type)
);

-- -----------------------------------------------------------------------------
-- 4. Quality Tables (from JPA Entity + V4__Add_quality_workflow_tables.sql)
-- -----------------------------------------------------------------------------

-- Quality Specs table (base from JPA entity, with V4 workflow columns)
CREATE TABLE IF NOT EXISTS quality_specs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    resource_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    owner VARCHAR(100) NOT NULL,
    team VARCHAR(100) DEFAULT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    schedule_cron VARCHAR(100) DEFAULT NULL,
    schedule_timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- V4 Workflow Integration fields
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    paused_by VARCHAR(255) DEFAULT NULL,
    paused_at TIMESTAMP NULL DEFAULT NULL,
    pause_reason VARCHAR(500) DEFAULT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    s3_path VARCHAR(500) DEFAULT NULL,
    airflow_dag_id VARCHAR(255) DEFAULT NULL,
    -- Audit fields
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    deleted_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    INDEX idx_quality_specs_name (name),
    INDEX idx_quality_specs_resource_name (resource_name),
    INDEX idx_quality_specs_resource_type (resource_type),
    INDEX idx_quality_specs_owner (owner),
    INDEX idx_quality_specs_enabled (enabled),
    INDEX idx_quality_specs_status (status),
    INDEX idx_quality_specs_source_type (source_type),
    INDEX idx_quality_specs_updated_at (updated_at)
);

-- Quality Spec Tags table (from JPA @ElementCollection)
CREATE TABLE IF NOT EXISTS quality_spec_tags (
    quality_spec_id BIGINT NOT NULL,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (quality_spec_id, tag),
    INDEX idx_quality_spec_tags_tag (tag)
);

-- Quality Runs table (V4 schema)
CREATE TABLE IF NOT EXISTS quality_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(100) NOT NULL,
    quality_spec_id BIGINT NOT NULL,
    spec_name VARCHAR(255) NOT NULL,
    target_resource VARCHAR(255) NOT NULL,
    target_resource_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    run_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    triggered_by VARCHAR(255) NOT NULL,
    params TEXT,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    -- Stop tracking fields
    stopped_by VARCHAR(255),
    stopped_at TIMESTAMP NULL,
    stop_reason VARCHAR(500),
    -- Test result summary
    total_tests INT DEFAULT 0,
    passed_tests INT DEFAULT 0,
    failed_tests INT DEFAULT 0,
    -- Airflow integration fields
    airflow_dag_run_id VARCHAR(255),
    airflow_state VARCHAR(50),
    airflow_url VARCHAR(1000),
    last_synced_at TIMESTAMP NULL,
    airflow_cluster_id BIGINT,
    -- Audit fields
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_quality_runs_run_id UNIQUE (run_id),
    INDEX idx_quality_runs_run_id (run_id),
    INDEX idx_quality_runs_spec_id (quality_spec_id),
    INDEX idx_quality_runs_spec_name (spec_name),
    INDEX idx_quality_runs_target_resource (target_resource),
    INDEX idx_quality_runs_status (status),
    INDEX idx_quality_runs_run_type (run_type),
    INDEX idx_quality_runs_started_at (started_at),
    INDEX idx_quality_runs_triggered_by (triggered_by),
    INDEX idx_quality_runs_airflow_dag_run_id (airflow_dag_run_id),
    INDEX idx_quality_runs_airflow_cluster_id (airflow_cluster_id),
    INDEX idx_quality_runs_last_synced_at (last_synced_at),
    INDEX idx_quality_runs_spec_name_started_at (spec_name, started_at),
    INDEX idx_quality_runs_status_last_synced (status, last_synced_at)
);

-- Quality Test Results table (V4)
CREATE TABLE IF NOT EXISTS quality_test_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quality_run_id BIGINT NOT NULL,
    test_name VARCHAR(255) NOT NULL,
    test_type VARCHAR(30) NOT NULL,
    target_column VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    severity VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    -- Test metrics
    duration_seconds DOUBLE,
    rows_tested BIGINT,
    rows_failed BIGINT,
    -- Failure details
    failure_message TEXT,
    failed_rows_sample TEXT,
    -- Airflow Task integration fields
    airflow_task_id VARCHAR(255),
    airflow_task_state VARCHAR(50),
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    -- Audit fields
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_quality_test_results_run_test UNIQUE (quality_run_id, test_name),
    INDEX idx_quality_test_results_run_id (quality_run_id),
    INDEX idx_quality_test_results_status (status),
    INDEX idx_quality_test_results_test_type (test_type),
    INDEX idx_quality_test_results_severity (severity),
    INDEX idx_quality_test_results_test_name (test_name),
    INDEX idx_quality_test_results_task_id (airflow_task_id),
    INDEX idx_quality_test_results_started_at (started_at)
);

-- -----------------------------------------------------------------------------
-- 5. Execution Tables (from V5__Create_execution_tables.sql)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS execution_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(36) NOT NULL UNIQUE COMMENT '실행 ID (API 식별자)',
    execution_type VARCHAR(20) NOT NULL COMMENT '실행 타입 (DATASET/QUALITY/RAW_SQL)',
    resource_name VARCHAR(255) COMMENT '리소스 이름',
    status VARCHAR(20) NOT NULL COMMENT '실행 상태',
    started_at TIMESTAMP NOT NULL COMMENT '실행 시작 시간',
    completed_at TIMESTAMP NULL COMMENT '실행 완료 시간',
    duration_ms BIGINT COMMENT '실행 시간 (밀리초)',
    user_id BIGINT NOT NULL COMMENT '실행을 트리거한 사용자 ID',
    transpiled_sql TEXT NOT NULL COMMENT '실행된 SQL',
    parameters JSON COMMENT '실행 파라미터',
    reason VARCHAR(500) COMMENT '실행 사유',
    dialect VARCHAR(20) COMMENT 'SQL Dialect',
    error_code VARCHAR(20) COMMENT '에러 코드',
    error_message TEXT COMMENT '에러 메시지',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT,
    INDEX idx_execution_history_execution_id (execution_id),
    INDEX idx_execution_history_execution_type (execution_type),
    INDEX idx_execution_history_resource_name (resource_name),
    INDEX idx_execution_history_status (status),
    INDEX idx_execution_history_started_at (started_at),
    INDEX idx_execution_history_user_id (user_id),
    INDEX idx_execution_history_type_status (execution_type, status),
    INDEX idx_execution_history_resource_status (resource_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='실행 이력 테이블';

CREATE TABLE IF NOT EXISTS execution_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(36) NOT NULL UNIQUE COMMENT '실행 ID (FK to execution_history)',
    result_data JSON NOT NULL COMMENT '결과 데이터',
    row_count INT NOT NULL COMMENT '결과 행 수',
    `schema` JSON COMMENT '결과 스키마',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT,
    INDEX idx_execution_result_execution_id (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='실행 결과 테이블';

-- -----------------------------------------------------------------------------
-- 6. Query Tables
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS query_executions (
    query_id VARCHAR(255) PRIMARY KEY,
    `sql` TEXT NOT NULL COMMENT 'SQL expression',
    status VARCHAR(20) NOT NULL COMMENT 'Query status',
    submitted_by VARCHAR(255) NOT NULL COMMENT 'Submitted by email',
    submitted_at TIMESTAMP(6) NOT NULL COMMENT 'Submission timestamp',
    started_at TIMESTAMP(6) NULL COMMENT 'Execution start timestamp',
    completed_at TIMESTAMP(6) NULL COMMENT 'Execution completion timestamp',
    duration_seconds DOUBLE NULL COMMENT 'Execution duration in seconds',
    rows_returned BIGINT NULL COMMENT 'Number of rows returned',
    bytes_scanned VARCHAR(255) NULL COMMENT 'Bytes scanned',
    engine VARCHAR(20) NOT NULL COMMENT 'Query engine (BIGQUERY, TRINO, etc.)',
    cost_usd DOUBLE NULL COMMENT 'Query cost in USD',
    execution_details JSON NULL COMMENT 'Execution details',
    error_details JSON NULL COMMENT 'Error details',
    cancelled_by VARCHAR(255) NULL COMMENT 'Cancelled by user',
    cancelled_at TIMESTAMP(6) NULL COMMENT 'Cancellation timestamp',
    cancellation_reason VARCHAR(500) NULL COMMENT 'Cancellation reason',
    is_system_query BOOLEAN DEFAULT FALSE COMMENT 'Is system query',
    INDEX idx_query_submitted_by (submitted_by),
    INDEX idx_query_status (status),
    INDEX idx_query_submitted_at (submitted_at),
    INDEX idx_query_is_system_query (is_system_query)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Query execution metadata';

-- -----------------------------------------------------------------------------
-- 7. Catalog Tables
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS catalog_sample_queries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_ref VARCHAR(500) NOT NULL COMMENT 'Fully qualified table reference (project.dataset.table)',
    title VARCHAR(200) NOT NULL COMMENT 'Query title/name',
    `sql` VARCHAR(10000) NOT NULL COMMENT 'SQL query text',
    author VARCHAR(200) NOT NULL COMMENT 'Query author email',
    run_count INT NOT NULL DEFAULT 0 COMMENT 'Number of times this query has been run',
    last_run TIMESTAMP NULL COMMENT 'Last time the query was run',
    description VARCHAR(1000) COMMENT 'Query description',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sample_query_table_ref (table_ref),
    INDEX idx_sample_query_run_count (run_count),
    INDEX idx_sample_query_author (author)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Catalog sample queries';

-- -----------------------------------------------------------------------------
-- 8. Sample Data (Idempotent inserts)
-- -----------------------------------------------------------------------------

-- Sample Pipelines
INSERT IGNORE INTO pipelines (id, name, description, status, owner, is_active) VALUES
    (1, 'sample-etl-pipeline', 'ETL 파이프라인 샘플', 'DRAFT', 'admin', true),
    (2, 'data-quality-check', '데이터 품질 검사 파이프라인', 'ACTIVE', 'admin', true);

-- Sample Datasets
INSERT IGNORE INTO datasets (id, name, description, type, format, location, owner, is_active) VALUES
    (1, 'user-events', '사용자 이벤트 데이터', 'SOURCE', 'JSON', '/data/events/user_events.json', 'admin', true),
    (2, 'processed-analytics', '처리된 분석 데이터', 'TARGET', 'PARQUET', '/data/analytics/processed/', 'admin', true);

-- Sample Teams
INSERT IGNORE INTO team (id, name, display_name, description) VALUES
    (1, 'analytics', 'Analytics Team', '데이터 분석팀'),
    (2, 'marketing', 'Marketing Team', '마케팅팀');

-- Sample Lineage Nodes
INSERT IGNORE INTO lineage_nodes (name, type, owner, team, description) VALUES
    ('catalog.schema.orders', 'TABLE', 'data-eng@company.com', 'data-engineering', 'Customer orders table'),
    ('catalog.schema.customers', 'TABLE', 'data-eng@company.com', 'data-engineering', 'Customer information table'),
    ('catalog.schema.order_metrics', 'DATASET', 'analytics@company.com', 'analytics', 'Daily order metrics dataset'),
    ('catalog.schema.customer_lifetime_value', 'METRIC', 'analytics@company.com', 'analytics', 'Customer lifetime value metric'),
    ('catalog.schema.order_summary_view', 'VIEW', 'data-eng@company.com', 'data-engineering', 'Aggregated order summary view');

INSERT IGNORE INTO lineage_node_tags (lineage_node_name, tag) VALUES
    ('catalog.schema.orders', 'production'),
    ('catalog.schema.orders', 'e-commerce'),
    ('catalog.schema.customers', 'production'),
    ('catalog.schema.customers', 'pii'),
    ('catalog.schema.order_metrics', 'analytics'),
    ('catalog.schema.customer_lifetime_value', 'kpi'),
    ('catalog.schema.order_summary_view', 'reporting');

INSERT IGNORE INTO lineage_edges (id, source, target, edge_type, description) VALUES
    (1, 'catalog.schema.orders', 'catalog.schema.order_metrics', 'DIRECT', 'Order metrics derived from orders table'),
    (2, 'catalog.schema.customers', 'catalog.schema.customer_lifetime_value', 'DIRECT', 'CLV calculated from customer data'),
    (3, 'catalog.schema.orders', 'catalog.schema.order_summary_view', 'DIRECT', 'Summary view aggregates order data'),
    (4, 'catalog.schema.customers', 'catalog.schema.order_summary_view', 'DIRECT', 'Summary view includes customer data'),
    (5, 'catalog.schema.order_metrics', 'catalog.schema.customer_lifetime_value', 'INDIRECT', 'CLV uses order metrics for calculation');

-- =============================================================================
-- End of DDL Script
-- =============================================================================
