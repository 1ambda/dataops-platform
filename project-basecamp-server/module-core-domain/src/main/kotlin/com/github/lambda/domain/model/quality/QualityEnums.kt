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
    INFO,
}

/**
 * Quality Spec 상태
 *
 * - ACTIVE: 활성화됨 (스케줄 실행 가능)
 * - PAUSED: 일시정지 (수동 트리거만 가능)
 * - DISABLED: 비활성화 (모든 실행 불가)
 */
enum class QualitySpecStatus {
    ACTIVE,
    PAUSED,
    DISABLED,
}

/**
 * Quality Run 실행 상태
 *
 * @deprecated Use WorkflowRunStatus instead for new code.
 * Kept for backward compatibility with existing quality run data.
 */
@Deprecated(
    message = "Use WorkflowRunStatus instead",
    replaceWith =
        ReplaceWith(
            "WorkflowRunStatus",
            "com.github.lambda.domain.model.workflow.WorkflowRunStatus",
        ),
)
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
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    ERROR,
    SKIPPED,
}
