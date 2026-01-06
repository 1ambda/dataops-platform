package com.dataops.basecamp.common.enums

/**
 * 실행 상태
 *
 * Ad-Hoc 쿼리 및 Dataset/Quality/Raw SQL 실행 상태를 표현합니다.
 */
enum class ExecutionStatus {
    /** 실행 대기 중 */
    PENDING,

    /** 실행 중 */
    RUNNING,

    /** 실행 완료 (성공) */
    COMPLETED,

    /** 실행 성공 (COMPLETED와 동일) */
    SUCCESS,

    /** 실행 실패 */
    FAILED,

    /** 실행 시간 초과 */
    TIMEOUT,

    /** 실행 취소됨 */
    CANCELLED,

    /** 검증만 완료 (dry-run) */
    VALIDATED,
}
