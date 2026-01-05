package com.dataops.basecamp.domain.entity.quality

import com.dataops.basecamp.common.enums.Severity
import com.dataops.basecamp.common.enums.TestStatus
import com.dataops.basecamp.common.enums.TestType
import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Duration
import java.time.LocalDateTime

/**
 * Quality Test Result Entity
 *
 * Quality Run의 개별 테스트 결과를 관리하는 엔티티 (Task 레벨)
 * Airflow Task와 1:1 매핑됩니다.
 */
@Entity
@Table(
    name = "quality_test_results",
    indexes = [
        Index(name = "idx_quality_test_results_run_id", columnList = "quality_run_id"),
        Index(name = "idx_quality_test_results_status", columnList = "status"),
        Index(name = "idx_quality_test_results_test_type", columnList = "test_type"),
        Index(name = "idx_quality_test_results_severity", columnList = "severity"),
        Index(name = "idx_quality_test_results_test_name", columnList = "test_name"),
        Index(name = "idx_quality_test_results_task_id", columnList = "airflow_task_id"),
        Index(name = "idx_quality_test_results_started_at", columnList = "started_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_quality_test_results_run_test",
            columnNames = ["quality_run_id", "test_name"],
        ),
    ],
)
class QualityTestResultEntity(
    /**
     * Quality Run ID (Long FK)
     */
    @Column(name = "quality_run_id", nullable = false)
    val qualityRunId: Long,
    /**
     * 테스트 이름
     */
    @NotBlank(message = "Test name is required")
    @Size(max = 255, message = "Test name must not exceed 255 characters")
    @Column(name = "test_name", nullable = false, length = 255)
    val testName: String,
    /**
     * 테스트 타입 (NOT_NULL, UNIQUE, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 30)
    val testType: TestType,
    /**
     * 대상 컬럼 (해당하는 경우)
     */
    @Size(max = 255, message = "Target column must not exceed 255 characters")
    @Column(name = "target_column", length = 255)
    val targetColumn: String? = null,
    /**
     * 테스트 결과 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TestStatus = TestStatus.PENDING,
    /**
     * 테스트 심각도
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    val severity: Severity = Severity.ERROR,
    /**
     * 테스트 실행 시간 (초)
     */
    @PositiveOrZero(message = "Duration seconds must be positive or zero")
    @Column(name = "duration_seconds")
    var durationSeconds: Double? = null,
    /**
     * 테스트된 행 수
     */
    @PositiveOrZero(message = "Rows tested must be positive or zero")
    @Column(name = "rows_tested")
    var rowsTested: Long? = null,
    /**
     * 실패한 행 수
     */
    @PositiveOrZero(message = "Rows failed must be positive or zero")
    @Column(name = "rows_failed")
    var rowsFailed: Long? = null,
    /**
     * 실패 메시지
     */
    @Column(name = "failure_message", columnDefinition = "TEXT")
    var failureMessage: String? = null,
    /**
     * 실패한 행 샘플 (JSON)
     */
    @Column(name = "failed_rows_sample", columnDefinition = "TEXT")
    var failedRowsSample: String? = null,
    /** Airflow Task ID (Airflow Task Integration) */
    @Size(max = 255, message = "Airflow Task ID must not exceed 255 characters")
    @Column(name = "airflow_task_id", length = 255)
    var airflowTaskId: String? = null,
    /**
     * Airflow Task 상태
     */
    @Size(max = 50, message = "Airflow Task state must not exceed 50 characters")
    @Column(name = "airflow_task_state", length = 50)
    var airflowTaskState: String? = null,
    /**
     * 테스트 시작 시간
     */
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
    /**
     * 테스트 종료 시간
     */
    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null,
) : BaseEntity() {
    // === State Transition Methods ===

    /**
     * 테스트 시작
     */
    fun start() {
        require(status == TestStatus.PENDING) {
            "Cannot start test. Current status: $status"
        }
        status = TestStatus.RUNNING
        startedAt = LocalDateTime.now()
    }

    /**
     * 테스트 통과
     */
    fun pass(
        rowsTested: Long,
        durationSeconds: Double,
    ) {
        require(status == TestStatus.RUNNING) {
            "Cannot pass test. Current status: $status"
        }
        this.status = TestStatus.PASSED
        this.rowsTested = rowsTested
        this.rowsFailed = 0
        this.durationSeconds = durationSeconds
        this.endedAt = LocalDateTime.now()
    }

    /**
     * 테스트 실패
     */
    fun fail(
        rowsTested: Long,
        rowsFailed: Long,
        durationSeconds: Double,
        failureMessage: String?,
        failedRowsSample: String? = null,
    ) {
        require(status == TestStatus.RUNNING || status == TestStatus.PENDING) {
            "Cannot fail test. Current status: $status"
        }
        this.status = TestStatus.FAILED
        this.rowsTested = rowsTested
        this.rowsFailed = rowsFailed
        this.durationSeconds = durationSeconds
        this.failureMessage = failureMessage
        this.failedRowsSample = failedRowsSample
        this.endedAt = LocalDateTime.now()
    }

    /**
     * 테스트 오류 (실행 자체가 실패한 경우)
     */
    fun error(
        errorMessage: String,
        durationSeconds: Double? = null,
    ) {
        this.status = TestStatus.ERROR
        this.failureMessage = errorMessage
        this.durationSeconds = durationSeconds
        this.endedAt = LocalDateTime.now()
    }

    /**
     * 테스트 스킵
     */
    fun skip(reason: String? = null) {
        this.status = TestStatus.SKIPPED
        this.failureMessage = reason
        this.endedAt = LocalDateTime.now()
    }

    // === Airflow Sync Methods ===

    /**
     * Airflow Task 정보로 업데이트
     */
    fun updateFromAirflow(
        taskState: String,
        startedAt: LocalDateTime?,
        endedAt: LocalDateTime?,
    ) {
        this.airflowTaskState = taskState
        this.status = mapAirflowTaskState(taskState)

        if (startedAt != null && this.startedAt == null) {
            this.startedAt = startedAt
        }
        if (endedAt != null && this.endedAt == null) {
            this.endedAt = endedAt
        }
    }

    /**
     * Airflow Task 상태를 TestStatus로 매핑
     */
    private fun mapAirflowTaskState(taskState: String): TestStatus =
        when (taskState.uppercase()) {
            "QUEUED", "SCHEDULED" -> TestStatus.PENDING
            "RUNNING" -> TestStatus.RUNNING
            "SUCCESS" -> TestStatus.PASSED
            "FAILED" -> TestStatus.FAILED
            "UP_FOR_RETRY", "UP_FOR_RESCHEDULE" -> TestStatus.RUNNING
            "UPSTREAM_FAILED" -> TestStatus.ERROR
            "SKIPPED" -> TestStatus.SKIPPED
            else -> TestStatus.ERROR
        }

    // === Status Check Methods ===

    /**
     * 대기 중인지 확인
     */
    fun isPending(): Boolean = status == TestStatus.PENDING

    /**
     * 실행 중인지 확인
     */
    fun isRunning(): Boolean = status == TestStatus.RUNNING

    /**
     * 통과했는지 확인
     */
    fun isPassed(): Boolean = status == TestStatus.PASSED

    /**
     * 실패했는지 확인
     */
    fun isFailed(): Boolean = status == TestStatus.FAILED

    /**
     * 오류 상태인지 확인
     */
    fun isError(): Boolean = status == TestStatus.ERROR

    /**
     * 스킵되었는지 확인
     */
    fun isSkipped(): Boolean = status == TestStatus.SKIPPED

    /**
     * 완료되었는지 확인 (통과, 실패, 오류, 스킵 포함)
     */
    fun isFinished(): Boolean =
        status == TestStatus.PASSED ||
            status == TestStatus.FAILED ||
            status == TestStatus.ERROR ||
            status == TestStatus.SKIPPED

    // === Utility Methods ===

    /**
     * 실행 시간 계산 (초)
     * 저장된 값이 있으면 반환, 없으면 시작/종료 시간으로 계산
     */
    fun calculateDurationSeconds(): Double? {
        if (durationSeconds != null) return durationSeconds

        val start = startedAt ?: return null
        val end = endedAt ?: LocalDateTime.now()
        return Duration.between(start, end).toMillis() / 1000.0
    }

    /**
     * 실패율 계산 (0.0 ~ 1.0)
     */
    fun getFailureRate(): Double {
        val tested = rowsTested ?: return 0.0
        val failed = rowsFailed ?: return 0.0
        if (tested == 0L) return 0.0
        return failed.toDouble() / tested.toDouble()
    }

    /**
     * 심각도가 ERROR인지 확인
     */
    fun isErrorSeverity(): Boolean = severity == Severity.ERROR

    /**
     * 심각도가 WARN인지 확인
     */
    fun isWarnSeverity(): Boolean = severity == Severity.WARN

    /**
     * 심각도가 INFO인지 확인
     */
    fun isInfoSeverity(): Boolean = severity == Severity.INFO
}
