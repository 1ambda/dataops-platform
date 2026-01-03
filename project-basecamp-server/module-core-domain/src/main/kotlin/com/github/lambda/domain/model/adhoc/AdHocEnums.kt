package com.github.lambda.domain.model.adhoc

/**
 * Ad-Hoc 쿼리 실행 상태
 */
enum class ExecutionStatus {
    /** 실행 대기 중 */
    PENDING,

    /** 실행 중 */
    RUNNING,

    /** 실행 완료 */
    COMPLETED,

    /** 실행 실패 */
    FAILED,

    /** 실행 시간 초과 */
    TIMEOUT,

    /** 실행 취소됨 */
    CANCELLED,

    /** 검증만 완료 (dry-run) */
    VALIDATED,
}
