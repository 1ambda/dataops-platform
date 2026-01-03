package com.github.lambda.domain.model.health

/**
 * 헬스체크 상태 열거형
 */
enum class HealthStatus {
    UP,
    DOWN,
    UNKNOWN,
    ;

    companion object {
        /**
         * 컴포넌트 상태 목록에서 전체 상태를 결정합니다.
         * - 모든 컴포넌트가 UP이면 UP
         * - 하나라도 DOWN이면 DOWN
         * - 그 외에는 UNKNOWN
         */
        fun fromComponents(statuses: Collection<HealthStatus>): HealthStatus =
            when {
                statuses.isEmpty() -> UNKNOWN
                statuses.all { it == UP } -> UP
                statuses.any { it == DOWN } -> DOWN
                else -> UNKNOWN
            }
    }
}
