package com.github.lambda.domain.model.workflow

import java.time.Instant

/**
 * S3에서 Workflow Spec을 동기화한 결과
 */
data class SpecSyncResult(
    val totalProcessed: Int,
    val created: Int,
    val updated: Int,
    val failed: Int,
    val errors: List<SpecSyncError>,
    val syncedAt: Instant,
) {
    /**
     * 동기화 성공 여부 (에러가 없으면 성공)
     */
    fun isSuccess(): Boolean = errors.isEmpty()

    /**
     * 동기화 결과 요약 문자열
     */
    fun summary(): String =
        buildString {
            append("SpecSync completed at $syncedAt: ")
            append("processed=$totalProcessed, created=$created, updated=$updated, failed=$failed")
            if (errors.isNotEmpty()) {
                append(", errors=${errors.size}")
            }
        }

    companion object {
        /**
         * 빈 결과 생성 (동기화할 Spec이 없는 경우)
         */
        fun empty(syncedAt: Instant = Instant.now()): SpecSyncResult =
            SpecSyncResult(
                totalProcessed = 0,
                created = 0,
                updated = 0,
                failed = 0,
                errors = emptyList(),
                syncedAt = syncedAt,
            )
    }
}

/**
 * Spec 동기화 에러 정보
 */
data class SpecSyncError(
    val specPath: String,
    val message: String,
    val errorType: SpecSyncErrorType = SpecSyncErrorType.UNKNOWN,
) {
    override fun toString(): String = "[$errorType] $specPath: $message"
}

/**
 * Spec 동기화 에러 유형
 */
enum class SpecSyncErrorType {
    /** YAML 파싱 실패 */
    PARSE_ERROR,

    /** 유효성 검사 실패 */
    VALIDATION_ERROR,

    /** 저장소 접근 오류 */
    STORAGE_ERROR,

    /** Airflow 연동 오류 */
    AIRFLOW_ERROR,

    /** 알 수 없는 오류 */
    UNKNOWN,
}
