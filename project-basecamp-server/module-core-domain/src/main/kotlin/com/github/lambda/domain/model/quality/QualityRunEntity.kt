package com.github.lambda.domain.model.quality

import com.github.lambda.domain.model.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Quality Run Entity
 *
 * Quality Spec의 실행 이력을 관리하는 엔티티
 */
@Entity
@Table(
    name = "quality_runs",
    indexes = [
        Index(name = "idx_quality_runs_run_id", columnList = "run_id", unique = true),
        Index(name = "idx_quality_runs_spec_id", columnList = "spec_id"),
        Index(name = "idx_quality_runs_resource_name", columnList = "resource_name"),
        Index(name = "idx_quality_runs_status", columnList = "status"),
        Index(name = "idx_quality_runs_overall_status", columnList = "overall_status"),
        Index(name = "idx_quality_runs_started_at", columnList = "started_at"),
        Index(name = "idx_quality_runs_executed_by", columnList = "executed_by"),
    ],
)
class QualityRunEntity(
    @NotBlank(message = "Run ID is required")
    @Size(max = 255, message = "Run ID must not exceed 255 characters")
    @Column(name = "run_id", nullable = false, unique = true, length = 255)
    var runId: String = "",
    @NotBlank(message = "Resource name is required")
    @Size(max = 255, message = "Resource name must not exceed 255 characters")
    @Column(name = "resource_name", nullable = false, length = 255)
    var resourceName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RunStatus = RunStatus.RUNNING,
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", length = 20)
    var overallStatus: TestStatus? = null,
    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @PositiveOrZero(message = "Duration must be positive or zero")
    @Column(name = "duration_seconds")
    var durationSeconds: Double? = null,
    @PositiveOrZero(message = "Passed tests count must be positive or zero")
    @Column(name = "passed_tests", nullable = false)
    var passedTests: Int = 0,
    @PositiveOrZero(message = "Failed tests count must be positive or zero")
    @Column(name = "failed_tests", nullable = false)
    var failedTests: Int = 0,
    @NotBlank(message = "Executed by is required")
    @Size(max = 100, message = "Executed by must not exceed 100 characters")
    @Column(name = "executed_by", nullable = false, length = 100)
    var executedBy: String = "",
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id", nullable = false)
    var spec: QualitySpecEntity? = null

    @OneToMany(
        mappedBy = "run",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    var results: MutableList<TestResultEntity> = mutableListOf()

    /**
     * 실행 완료 처리
     */
    fun complete(
        overallStatus: TestStatus,
        passedCount: Int,
        failedCount: Int,
    ) {
        this.completedAt = Instant.now()
        this.status = RunStatus.COMPLETED
        this.overallStatus = overallStatus
        this.passedTests = passedCount
        this.failedTests = failedCount
        this.durationSeconds = getDuration()
    }

    /**
     * 실행 실패 처리
     */
    fun fail() {
        this.completedAt = Instant.now()
        this.status = RunStatus.FAILED
        this.overallStatus = TestStatus.ERROR
        this.durationSeconds = getDuration()
    }

    /**
     * 실행 타임아웃 처리
     */
    fun timeout() {
        this.completedAt = Instant.now()
        this.status = RunStatus.TIMEOUT
        this.overallStatus = TestStatus.ERROR
        this.durationSeconds = getDuration()
    }

    /**
     * 테스트 결과 추가
     */
    fun addResult(result: TestResultEntity) {
        results.add(result)
        result.run = this
    }

    /**
     * 실행 시간 계산 (초)
     */
    private fun getDuration(): Double? =
        if (completedAt != null) {
            (completedAt!!.toEpochMilli() - startedAt.toEpochMilli()) / 1000.0
        } else {
            null
        }

    /**
     * 실행 중인지 확인
     */
    fun isRunning(): Boolean = status == RunStatus.RUNNING

    /**
     * 완료되었는지 확인
     */
    fun isCompleted(): Boolean = status == RunStatus.COMPLETED

    /**
     * 실패했는지 확인 (실패 또는 타임아웃)
     */
    fun isFailed(): Boolean = status == RunStatus.FAILED || status == RunStatus.TIMEOUT
}
