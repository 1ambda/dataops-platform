package com.github.lambda.domain.entity.pipeline

import com.github.lambda.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 파이프라인 내 작업 엔티티
 */
@Entity
@Table(
    name = "jobs",
    indexes = [
        Index(name = "idx_job_pipeline_order", columnList = "pipeline_id, execution_order"),
        Index(name = "idx_job_status", columnList = "status"),
        Index(name = "idx_job_pipeline_status", columnList = "pipeline_id, status"),
    ],
)
class JobEntity(
    @NotBlank(message = "Job name is required")
    @Size(max = 100, message = "Job name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description", length = 500)
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: JobType = JobType.CUSTOM_SCRIPT,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: JobStatus = JobStatus.PENDING,
    @Min(value = 0, message = "Order must be non-negative")
    @Column(name = "execution_order", nullable = false)
    var executionOrder: Int = 0,
    @Column(name = "config", columnDefinition = "JSON")
    var config: String? = null,
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
    /**
     * 소속 파이프라인 ID (FK)
     */
    @Column(name = "pipeline_id", nullable = false)
    var pipelineId: Long = 0L,
) : BaseEntity() {
    /**
     * Job 시작
     */
    fun start() {
        this.status = JobStatus.RUNNING
        this.startedAt = LocalDateTime.now()
        this.finishedAt = null
        this.errorMessage = null
    }

    /**
     * Job 성공 완료
     */
    fun complete() {
        this.status = JobStatus.SUCCESS
        this.finishedAt = LocalDateTime.now()
        this.errorMessage = null
    }

    /**
     * Job 실패
     */
    fun fail(errorMessage: String) {
        this.status = JobStatus.FAILED
        this.finishedAt = LocalDateTime.now()
        this.errorMessage = errorMessage
    }

    /**
     * Job 취소
     */
    fun cancel() {
        this.status = JobStatus.CANCELLED
        this.finishedAt = LocalDateTime.now()
    }

    /**
     * Job 스킵
     */
    fun skip() {
        this.status = JobStatus.SKIPPED
        this.finishedAt = LocalDateTime.now()
    }

    /**
     * 실행 시간 계산 (초 단위)
     */
    fun getExecutionDurationSeconds(): Long? =
        if (startedAt != null && finishedAt != null) {
            java.time.Duration
                .between(startedAt, finishedAt)
                .seconds
        } else {
            null
        }

    /**
     * Job이 실행 중인지 확인
     */
    fun isRunning(): Boolean = status == JobStatus.RUNNING

    /**
     * Job이 완료되었는지 확인 (성공/실패/취소/스킵)
     */
    fun isCompleted(): Boolean =
        status in
            listOf(
                JobStatus.SUCCESS,
                JobStatus.FAILED,
                JobStatus.CANCELLED,
                JobStatus.SKIPPED,
            )
}

/**
 * 작업 유형
 */
enum class JobType {
    DATA_EXTRACTION, // 데이터 추출
    DATA_TRANSFORMATION, // 데이터 변환
    DATA_LOADING, // 데이터 적재
    DATA_VALIDATION, // 데이터 검증
    DATA_QUALITY_CHECK, // 데이터 품질 체크
    NOTIFICATION, // 알림
    CUSTOM_SCRIPT, // 커스텀 스크립트
}

/**
 * 작업 상태
 */
enum class JobStatus {
    PENDING, // 대기중
    RUNNING, // 실행중
    SUCCESS, // 성공
    FAILED, // 실패
    CANCELLED, // 취소됨
    SKIPPED, // 스킵됨
}
