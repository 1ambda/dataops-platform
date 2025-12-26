CREATE TABLE IF NOT EXISTS user
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


CREATE UNIQUE INDEX user_authority_unique_idx
    ON user_authority (user_id, authority);

INSERT INTO user (username, email, password, role, enabled)
VALUES ('1ambda', '1ambda@github.com', NULL, 'ADMIN', 1);

INSERT INTO user_authority (user_id, authority)
VALUES (1, 'PERMISSION_A'),
       (1, 'PERMISSION_B');



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

CREATE TABLE resource
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

-- 파이프라인 테이블
CREATE TABLE pipelines (
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

-- 작업 테이블
CREATE TABLE jobs (
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
                      FOREIGN KEY (pipeline_id) REFERENCES pipelines(id) ON DELETE CASCADE,
                      INDEX idx_job_pipeline (pipeline_id),
                      INDEX idx_job_status (status),
                      INDEX idx_job_type (type),
                      INDEX idx_job_execution_order (execution_order),
                      UNIQUE KEY uk_job_pipeline_order (pipeline_id, execution_order)
);

-- 데이터셋 테이블
CREATE TABLE datasets (
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

-- 초기 데이터 삽입
INSERT INTO pipelines (name, description, status, owner, is_active) VALUES
                                                                        ('sample-etl-pipeline', 'ETL 파이프라인 샘플', 'DRAFT', 'admin', true),
                                                                        ('data-quality-check', '데이터 품질 검사 파이프라인', 'ACTIVE', 'admin', true);

INSERT INTO datasets (name, description, type, format, location, owner, is_active) VALUES
                                                                                       ('user-events', '사용자 이벤트 데이터', 'SOURCE', 'JSON', '/data/events/user_events.json', 'admin', true),
                                                                                       ('processed-analytics', '처리된 분석 데이터', 'TARGET', 'PARQUET', '/data/analytics/processed/', 'admin', true);
