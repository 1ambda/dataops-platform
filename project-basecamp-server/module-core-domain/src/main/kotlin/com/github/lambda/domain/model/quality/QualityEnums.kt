package com.github.lambda.domain.model.quality

/**
 * Quality Spec이 적용되는 리소스 타입
 */
enum class ResourceType {
    DATASET,
    METRIC,
}

/**
 * Quality 테스트 타입
 */
enum class TestType {
    NOT_NULL,
    UNIQUE,
    ACCEPTED_VALUES,
    RELATIONSHIPS,
    EXPRESSION,
    ROW_COUNT,
    SINGULAR,
}

/**
 * 테스트 실패 시 심각도
 */
enum class Severity {
    ERROR,
    WARN,
}

/**
 * Quality Run 실행 상태
 */
enum class RunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
}

/**
 * 개별 테스트 결과 상태
 */
enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED,
}
