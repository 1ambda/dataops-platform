package com.dataops.basecamp.common.enums

/**
 * Workflow 소스 타입
 */
enum class WorkflowSourceType {
    MANUAL,
    CODE,
}

/**
 * Workflow 상태
 */
enum class WorkflowStatus {
    ACTIVE,
    PAUSED,
    DISABLED,
}

/**
 * Workflow 실행 상태
 */
enum class WorkflowRunStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    STOPPING,
    STOPPED,
    SKIPPED,
    TIMEOUT,
    UNKNOWN,
}

/**
 * Workflow 실행 타입
 */
enum class WorkflowRunType {
    SCHEDULED,
    MANUAL,
    BACKFILL,
}
