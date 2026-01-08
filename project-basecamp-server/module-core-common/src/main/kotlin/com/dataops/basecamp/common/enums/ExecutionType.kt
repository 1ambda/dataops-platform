package com.dataops.basecamp.common.enums

/**
 * Execution Type
 *
 * 실행 타입을 나타냅니다.
 */
enum class ExecutionType {
    /**
     * Dataset 실행
     */
    DATASET,

    /**
     * Metric 실행
     */
    METRIC,

    /**
     * Quality Spec 실행
     */
    QUALITY,

    /**
     * Raw SQL 실행
     */
    RAW_SQL,
}
