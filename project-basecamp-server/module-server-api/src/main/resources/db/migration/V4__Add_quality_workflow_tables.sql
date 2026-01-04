-- ============================================================================
-- V4: Add Quality Workflow Tables
-- Version: 2.0.0
-- Description: Add tables and columns for Quality Workflow integration
--              - Extend quality_specs with workflow fields
--              - Create quality_runs table (v2.0 enhanced)
--              - Create quality_test_results table
-- ============================================================================

-- ============================================================================
-- 1. Modify quality_specs table - Add workflow integration fields
-- ============================================================================
ALTER TABLE quality_specs
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN paused_by VARCHAR(255),
    ADD COLUMN paused_at TIMESTAMP,
    ADD COLUMN pause_reason VARCHAR(500),
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN s3_path VARCHAR(500),
    ADD COLUMN airflow_dag_id VARCHAR(255);

-- Add indexes for new columns
CREATE INDEX idx_quality_specs_status ON quality_specs (status);
CREATE INDEX idx_quality_specs_source_type ON quality_specs (source_type);

-- Sync existing data: enabled = true -> ACTIVE, enabled = false -> DISABLED
UPDATE quality_specs SET status = 'ACTIVE' WHERE enabled = true;
UPDATE quality_specs SET status = 'DISABLED' WHERE enabled = false;


-- ============================================================================
-- 2. Drop and recreate quality_runs table with v2.0 schema
-- Note: This is a breaking change - existing data will be lost
-- ============================================================================
DROP TABLE IF EXISTS quality_runs;

CREATE TABLE quality_runs (
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
    started_at TIMESTAMP,
    ended_at TIMESTAMP,

    -- Stop tracking fields
    stopped_by VARCHAR(255),
    stopped_at TIMESTAMP,
    stop_reason VARCHAR(500),

    -- Test result summary
    total_tests INT DEFAULT 0,
    passed_tests INT DEFAULT 0,
    failed_tests INT DEFAULT 0,

    -- Airflow integration fields
    airflow_dag_run_id VARCHAR(255),
    airflow_state VARCHAR(50),
    airflow_url VARCHAR(1000),
    last_synced_at TIMESTAMP,
    airflow_cluster_id BIGINT,

    -- BaseEntity audit fields
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uk_quality_runs_run_id UNIQUE (run_id),
    CONSTRAINT fk_quality_runs_spec_id FOREIGN KEY (quality_spec_id) REFERENCES quality_specs(id)
);

-- Indexes for quality_runs
CREATE INDEX idx_quality_runs_run_id ON quality_runs (run_id);
CREATE INDEX idx_quality_runs_spec_id ON quality_runs (quality_spec_id);
CREATE INDEX idx_quality_runs_spec_name ON quality_runs (spec_name);
CREATE INDEX idx_quality_runs_target_resource ON quality_runs (target_resource);
CREATE INDEX idx_quality_runs_status ON quality_runs (status);
CREATE INDEX idx_quality_runs_run_type ON quality_runs (run_type);
CREATE INDEX idx_quality_runs_started_at ON quality_runs (started_at);
CREATE INDEX idx_quality_runs_triggered_by ON quality_runs (triggered_by);
CREATE INDEX idx_quality_runs_airflow_dag_run_id ON quality_runs (airflow_dag_run_id);
CREATE INDEX idx_quality_runs_airflow_cluster_id ON quality_runs (airflow_cluster_id);
CREATE INDEX idx_quality_runs_last_synced_at ON quality_runs (last_synced_at);
-- Composite indexes for common queries
CREATE INDEX idx_quality_runs_spec_name_started_at ON quality_runs (spec_name, started_at);
CREATE INDEX idx_quality_runs_status_last_synced ON quality_runs (status, last_synced_at);


-- ============================================================================
-- 3. Create quality_test_results table
-- ============================================================================
CREATE TABLE quality_test_results (
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
    started_at TIMESTAMP,
    ended_at TIMESTAMP,

    -- BaseEntity audit fields
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uk_quality_test_results_run_test UNIQUE (quality_run_id, test_name),
    CONSTRAINT fk_quality_test_results_run_id FOREIGN KEY (quality_run_id) REFERENCES quality_runs(id) ON DELETE CASCADE
);

-- Indexes for quality_test_results
CREATE INDEX idx_quality_test_results_run_id ON quality_test_results (quality_run_id);
CREATE INDEX idx_quality_test_results_status ON quality_test_results (status);
CREATE INDEX idx_quality_test_results_test_type ON quality_test_results (test_type);
CREATE INDEX idx_quality_test_results_severity ON quality_test_results (severity);
CREATE INDEX idx_quality_test_results_test_name ON quality_test_results (test_name);
CREATE INDEX idx_quality_test_results_task_id ON quality_test_results (airflow_task_id);
CREATE INDEX idx_quality_test_results_started_at ON quality_test_results (started_at);
