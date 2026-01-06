package com.dataops.basecamp.domain.entity.execution

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.common.enums.ExecutionType
import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * Execution History Entity
 *
 * Dataset/Quality/Raw SQL 실행 이력을 관리하는 엔티티
 *
 * Phase 1: Foundation
 * - 통합된 실행 이력 추적
 * - 실행 상태 및 메타데이터 저장
 * - 에러 추적 및 진단 정보 포함
 */
@Entity
@Table(
    name = "execution_history",
    indexes = [
        Index(name = "idx_execution_history_execution_id", columnList = "execution_id", unique = true),
        Index(name = "idx_execution_history_execution_type", columnList = "execution_type"),
        Index(name = "idx_execution_history_resource_name", columnList = "resource_name"),
        Index(name = "idx_execution_history_status", columnList = "status"),
        Index(name = "idx_execution_history_started_at", columnList = "started_at"),
        Index(name = "idx_execution_history_user_id", columnList = "user_id"),
        // Composite indexes for common queries
        Index(name = "idx_execution_history_type_status", columnList = "execution_type, status"),
        Index(name = "idx_execution_history_resource_status", columnList = "resource_name, status"),
    ],
)
class ExecutionHistoryEntity(
    /**
     * 실행 ID (API 식별자)
     * 형식: exec_{timestamp}_{random}
     */
    @field:NotBlank(message = "Execution ID is required")
    @field:Size(max = 36, message = "Execution ID must not exceed 36 characters")
    @Column(name = "execution_id", nullable = false, unique = true, length = 36)
    val executionId: String,
    /**
     * 실행 타입 (DATASET/QUALITY/RAW_SQL)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false, length = 20)
    val executionType: ExecutionType,
    /**
     * 리소스 이름 (Dataset/Quality Spec 이름)
     * Raw SQL인 경우 null
     */
    @field:Size(max = 255, message = "Resource name must not exceed 255 characters")
    @Column(name = "resource_name", nullable = true, length = 255)
    val resourceName: String?,
    /**
     * 실행 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ExecutionStatus,
    /**
     * 실행 시작 시간
     */
    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime,
    /**
     * 실행 완료 시간
     */
    @Column(name = "completed_at", nullable = true)
    var completedAt: LocalDateTime? = null,
    /**
     * 실행 시간 (밀리초)
     */
    @Column(name = "duration_ms", nullable = true)
    var durationMs: Long? = null,
    /**
     * 실행을 트리거한 사용자 ID
     */
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    /**
     * 실행된 SQL (Transpiled)
     */
    @Column(name = "transpiled_sql", nullable = false, columnDefinition = "TEXT")
    val transpiledSql: String,
    /**
     * 실행 파라미터 (JSON)
     */
    @Column(name = "parameters", nullable = true, columnDefinition = "JSON")
    val parameters: String? = null,
    /**
     * 실행 사유/설명
     */
    @field:Size(max = 500, message = "Reason must not exceed 500 characters")
    @Column(name = "reason", nullable = true, length = 500)
    val reason: String? = null,
    /**
     * SQL Dialect
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dialect", nullable = true, length = 20)
    val dialect: SqlDialect? = null,
    /**
     * 에러 코드
     */
    @field:Size(max = 20, message = "Error code must not exceed 20 characters")
    @Column(name = "error_code", nullable = true, length = 20)
    var errorCode: String? = null,
    /**
     * 에러 메시지
     */
    @Column(name = "error_message", nullable = true, columnDefinition = "TEXT")
    var errorMessage: String? = null,
) : BaseEntity() {
    /**
     * 실행 완료 처리 (성공)
     */
    fun complete() {
        require(status == ExecutionStatus.RUNNING) {
            "Cannot complete execution. Current status: $status"
        }
        status = ExecutionStatus.SUCCESS
        completedAt = LocalDateTime.now()
        durationMs =
            java.time.Duration
                .between(startedAt, completedAt)
                .toMillis()
    }

    /**
     * 실행 실패 처리
     */
    fun fail(
        errorCode: String?,
        errorMessage: String?,
    ) {
        require(status == ExecutionStatus.RUNNING || status == ExecutionStatus.PENDING) {
            "Cannot fail execution. Current status: $status"
        }
        status = ExecutionStatus.FAILED
        completedAt = LocalDateTime.now()
        durationMs =
            java.time.Duration
                .between(startedAt, completedAt)
                .toMillis()
        this.errorCode = errorCode
        this.errorMessage = errorMessage
    }

    /**
     * 실행 타임아웃 처리
     */
    fun timeout() {
        require(status == ExecutionStatus.RUNNING) {
            "Cannot timeout execution. Current status: $status"
        }
        status = ExecutionStatus.TIMEOUT
        completedAt = LocalDateTime.now()
        durationMs =
            java.time.Duration
                .between(startedAt, completedAt)
                .toMillis()
    }

    /**
     * 실행 취소 처리
     */
    fun cancel() {
        require(status == ExecutionStatus.RUNNING || status == ExecutionStatus.PENDING) {
            "Cannot cancel execution. Current status: $status"
        }
        status = ExecutionStatus.CANCELLED
        completedAt = LocalDateTime.now()
        durationMs =
            java.time.Duration
                .between(startedAt, completedAt)
                .toMillis()
    }

    /**
     * 실행 중인지 확인
     */
    fun isRunning(): Boolean = status == ExecutionStatus.RUNNING

    /**
     * 실행 완료 여부 확인 (성공/실패/취소/타임아웃 포함)
     */
    fun isFinished(): Boolean =
        status == ExecutionStatus.SUCCESS ||
            status == ExecutionStatus.COMPLETED ||
            status == ExecutionStatus.FAILED ||
            status == ExecutionStatus.CANCELLED ||
            status == ExecutionStatus.TIMEOUT

    /**
     * 성공 여부 확인
     */
    fun isSuccess(): Boolean = status == ExecutionStatus.SUCCESS || status == ExecutionStatus.COMPLETED

    /**
     * 실패 여부 확인
     */
    fun isFailed(): Boolean = status == ExecutionStatus.FAILED
}
