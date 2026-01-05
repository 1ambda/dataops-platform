package com.dataops.basecamp.common.enums

/**
 * Airflow DAG 실행 상태
 */
enum class AirflowDAGRunState {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    UP_FOR_RETRY,
    UPSTREAM_FAILED,
    SKIPPED,
}

/**
 * Airflow Task 실행 상태
 */
enum class AirflowTaskState {
    NONE,
    SCHEDULED,
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    UP_FOR_RETRY,
    UP_FOR_RESCHEDULE,
    UPSTREAM_FAILED,
    SKIPPED,
    REMOVED,
    RESTARTING,
    DEFERRED,
}

/**
 * Backfill 상태 Enum
 */
enum class BackfillState {
    QUEUED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

/**
 * Airflow cluster environment type
 *
 * 클러스터가 운영되는 환경을 나타냅니다.
 */
enum class AirflowEnvironment {
    /**
     * 개발 환경
     */
    DEVELOPMENT,

    /**
     * 운영 환경
     */
    PRODUCTION,
}
