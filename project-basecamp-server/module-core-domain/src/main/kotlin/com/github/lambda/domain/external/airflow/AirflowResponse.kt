package com.github.lambda.domain.external.airflow

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
data class AirflowDAGRunStatusResponse(
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
data class AirflowDagStatusResponse(
    val dagId: String,
    val isPaused: Boolean,
    val lastParsed: LocalDateTime?,
    val isActive: Boolean,
)

// ============ Phase 4: Airflow 3 API DTOs ============

/**
 * Airflow DAG Run 정보 (Airflow 3 API 응답)
 *
 * @param dagId DAG ID
 * @param dagRunId DAG Run ID (unique identifier)
 * @param state 실행 상태
 * @param logicalDate 논리적 실행 날짜 (Airflow 3에서는 execution_date 대신 사용)
 * @param startDate 실제 시작 시간
 * @param endDate 종료 시간
 * @param conf 실행 설정 (JSON)
 */
data class AirflowDagRunResponse(
    val dagId: String,
    val dagRunId: String,
    val state: AirflowDAGRunState,
    val logicalDate: LocalDateTime?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val conf: Map<String, Any>? = null,
)

/**
 * Airflow Task Instance 정보
 *
 * @param taskId Task ID
 * @param dagId DAG ID
 * @param dagRunId DAG Run ID
 * @param state Task 실행 상태
 * @param startDate 시작 시간
 * @param endDate 종료 시간
 * @param tryNumber 재시도 횟수
 * @param duration 실행 시간 (초)
 */
data class AirflowTaskInstanceResponse(
    val taskId: String,
    val dagId: String,
    val dagRunId: String,
    val state: AirflowTaskState,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val tryNumber: Int = 1,
    val duration: Double? = null,
)

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
 * Backfill 생성 응답 (Airflow 3 API)
 *
 * @param id Backfill ID
 * @param dagId DAG ID
 * @param fromDate 시작 날짜 (ISO 8601 형식)
 * @param toDate 종료 날짜 (ISO 8601 형식)
 * @param isPaused 일시정지 상태
 * @param createdAt 생성 시간 (ISO 8601 형식)
 */
data class BackfillCreateResponse(
    val id: String,
    val dagId: String,
    val fromDate: String,
    val toDate: String,
    val isPaused: Boolean = false,
    val createdAt: String,
)

/**
 * Backfill 상태 정보
 *
 * @param id Backfill ID
 * @param dagId DAG ID
 * @param fromDate 시작 날짜
 * @param toDate 종료 날짜
 * @param isPaused 일시정지 상태
 * @param completedAt 완료 시간
 * @param state Backfill 상태
 */
data class BackfillStatusResponse(
    val id: String,
    val dagId: String,
    val fromDate: String,
    val toDate: String,
    val isPaused: Boolean = false,
    val completedAt: String? = null,
    val state: BackfillState = BackfillState.RUNNING,
)

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
