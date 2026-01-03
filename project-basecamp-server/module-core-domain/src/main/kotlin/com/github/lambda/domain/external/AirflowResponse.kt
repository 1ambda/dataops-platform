package com.github.lambda.domain.external

import java.time.LocalDateTime

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
 * Airflow DAG 실행 상태 정보
 *
 * @param dagRunId DAG 실행 ID
 * @param state 실행 상태
 * @param startDate 시작 시간
 * @param endDate 종료 시간
 * @param executionDate 실행 예정 시간
 * @param logsUrl 로그 URL
 */
data class AirflowDAGRunStatus(
    val dagRunId: String,
    val state: AirflowDAGRunState,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val executionDate: LocalDateTime,
    val logsUrl: String?,
)

/**
 * Airflow DAG 상태 정보
 *
 * @param dagId DAG ID
 * @param isPaused 일시정지 여부
 * @param lastParsed 마지막 파싱 시간
 * @param isActive 활성 상태 여부
 */
data class AirflowDagStatus(
    val dagId: String,
    val isPaused: Boolean,
    val lastParsed: LocalDateTime?,
    val isActive: Boolean,
)