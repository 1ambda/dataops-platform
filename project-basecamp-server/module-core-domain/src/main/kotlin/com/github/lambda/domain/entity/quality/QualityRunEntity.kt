package com.github.lambda.domain.entity.quality

import com.github.lambda.domain.entity.BaseEntity
import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.workflow.WorkflowRunStatus
import com.github.lambda.domain.model.workflow.WorkflowRunType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Duration
import java.time.LocalDateTime

/**
 * Quality Run Entity
 *
 * Quality Spec의 개별 실행 이력을 관리하는 엔티티 (DAG 레벨)
 * Airflow DAG Run과 1:1 매핑됩니다.
 *
 * v2.0 Enhancement:
 * - Uses WorkflowRunStatus instead of deprecated RunStatus
 * - Adds Airflow integration fields
 * - Adds stop tracking fields
 * - Adds spec_name denormalization for query performance
 */
@Entity
@Table(
    name = "quality_runs",
    indexes = [
        Index(name = "idx_quality_runs_run_id", columnList = "run_id", unique = true),
        Index(name = "idx_quality_runs_spec_id", columnList = "quality_spec_id"),
        Index(name = "idx_quality_runs_spec_name", columnList = "spec_name"),
        Index(name = "idx_quality_runs_target_resource", columnList = "target_resource"),
        Index(name = "idx_quality_runs_status", columnList = "status"),
        Index(name = "idx_quality_runs_run_type", columnList = "run_type"),
        Index(name = "idx_quality_runs_started_at", columnList = "started_at"),
        Index(name = "idx_quality_runs_triggered_by", columnList = "triggered_by"),
        Index(name = "idx_quality_runs_airflow_dag_run_id", columnList = "airflow_dag_run_id"),
        Index(name = "idx_quality_runs_airflow_cluster_id", columnList = "airflow_cluster_id"),
        Index(name = "idx_quality_runs_last_synced_at", columnList = "last_synced_at"),
        // Composite indexes for common queries
        Index(name = "idx_quality_runs_spec_name_started_at", columnList = "spec_name, started_at"),
        Index(name = "idx_quality_runs_status_last_synced", columnList = "status, last_synced_at"),
    ],
)
class QualityRunEntity(
    /**
     * API 식별자 (String)
     * 형식: quality_run_{timestamp}_{random}
     */
    @NotBlank(message = "Run ID is required")
    @Size(max = 100, message = "Run ID must not exceed 100 characters")
    @Column(name = "run_id", nullable = false, unique = true, length = 100)
    val runId: String,
    /**
     * Quality Spec ID (Long FK)
     */
    @Column(name = "quality_spec_id", nullable = false)
    val qualitySpecId: Long,
    /**
     * Quality Spec 이름 (denormalized for query performance)
     */
    @NotBlank(message = "Spec name is required")
    @Size(max = 255, message = "Spec name must not exceed 255 characters")
    @Column(name = "spec_name", nullable = false, length = 255)
    val specName: String,
    /**
     * 대상 리소스 이름 (Dataset/Metric 이름)
     */
    @NotBlank(message = "Target resource is required")
    @Size(max = 255, message = "Target resource must not exceed 255 characters")
    @Column(name = "target_resource", nullable = false, length = 255)
    val targetResource: String,
    /**
     * 대상 리소스 타입 (DATASET/METRIC)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_resource_type", nullable = false, length = 20)
    val targetResourceType: ResourceType,
    /**
     * 실행 상태 (WorkflowRunStatus 사용)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: WorkflowRunStatus = WorkflowRunStatus.PENDING,
    /**
     * 실행 타입 (SCHEDULED/MANUAL/BACKFILL)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    val runType: WorkflowRunType = WorkflowRunType.MANUAL,
    /**
     * 실행을 트리거한 사용자
     */
    @NotBlank(message = "Triggered by is required")
    @Size(max = 255, message = "Triggered by must not exceed 255 characters")
    @Column(name = "triggered_by", nullable = false, length = 255)
    val triggeredBy: String,
    /**
     * 실행 파라미터 (JSON 문자열)
     */
    @Column(name = "params", columnDefinition = "TEXT")
    var params: String? = null,
    /**
     * 실행 시작 시간
     */
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
    /**
     * 실행 종료 시간
     */
    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null,
    /** 중지를 요청한 사용자 (Stop Tracking) */
    @Size(max = 255, message = "Stopped by must not exceed 255 characters")
    @Column(name = "stopped_by", length = 255)
    var stoppedBy: String? = null,
    /**
     * 중지 요청 시간
     */
    @Column(name = "stopped_at")
    var stoppedAt: LocalDateTime? = null,
    /**
     * 중지 사유
     */
    @Size(max = 500, message = "Stop reason must not exceed 500 characters")
    @Column(name = "stop_reason", length = 500)
    var stopReason: String? = null,
    /** 전체 테스트 수 (Test Result Summary) */
    @PositiveOrZero(message = "Total tests count must be positive or zero")
    @Column(name = "total_tests")
    var totalTests: Int = 0,
    /**
     * 통과한 테스트 수
     */
    @PositiveOrZero(message = "Passed tests count must be positive or zero")
    @Column(name = "passed_tests")
    var passedTests: Int = 0,
    /**
     * 실패한 테스트 수
     */
    @PositiveOrZero(message = "Failed tests count must be positive or zero")
    @Column(name = "failed_tests")
    var failedTests: Int = 0,
    /** Airflow DAG Run ID (Airflow Integration) */
    @Size(max = 255, message = "Airflow DAG Run ID must not exceed 255 characters")
    @Column(name = "airflow_dag_run_id", length = 255)
    var airflowDagRunId: String? = null,
    /**
     * Airflow의 원본 상태값
     */
    @Size(max = 50, message = "Airflow state must not exceed 50 characters")
    @Column(name = "airflow_state", length = 50)
    var airflowState: String? = null,
    /**
     * Airflow UI URL
     */
    @Size(max = 1000, message = "Airflow URL must not exceed 1000 characters")
    @Column(name = "airflow_url", length = 1000)
    var airflowUrl: String? = null,
    /**
     * 마지막 Airflow 동기화 시간
     */
    @Column(name = "last_synced_at")
    var lastSyncedAt: LocalDateTime? = null,
    /**
     * Airflow 클러스터 ID (FK)
     */
    @Column(name = "airflow_cluster_id")
    var airflowClusterId: Long? = null,
) : BaseEntity() {
    // === State Transition Methods ===

    /**
     * 실행 시작
     */
    fun start() {
        require(status == WorkflowRunStatus.PENDING) {
            "Cannot start run. Current status: $status"
        }
        status = WorkflowRunStatus.RUNNING
        startedAt = LocalDateTime.now()
    }

    /**
     * 실행 완료 (성공)
     */
    fun complete() {
        require(status == WorkflowRunStatus.RUNNING) {
            "Cannot complete run. Current status: $status"
        }
        status = WorkflowRunStatus.SUCCESS
        endedAt = LocalDateTime.now()
    }

    /**
     * 실행 완료 (테스트 결과 포함)
     */
    fun complete(
        passed: Int,
        failed: Int,
        total: Int,
    ) {
        require(status == WorkflowRunStatus.RUNNING) {
            "Cannot complete run. Current status: $status"
        }
        status = WorkflowRunStatus.SUCCESS
        endedAt = LocalDateTime.now()
        passedTests = passed
        failedTests = failed
        totalTests = total
    }

    /**
     * 실행 실패
     */
    fun fail() {
        require(status != WorkflowRunStatus.SUCCESS && status != WorkflowRunStatus.STOPPED) {
            "Cannot fail run. Current status: $status"
        }
        status = WorkflowRunStatus.FAILED
        endedAt = LocalDateTime.now()
    }

    /**
     * 실행 타임아웃
     */
    fun timeout() {
        require(status == WorkflowRunStatus.RUNNING || status == WorkflowRunStatus.PENDING) {
            "Cannot timeout run. Current status: $status"
        }
        status = WorkflowRunStatus.TIMEOUT
        endedAt = LocalDateTime.now()
    }

    /**
     * 실행 중지 요청
     */
    fun requestStop(
        stoppedBy: String,
        reason: String? = null,
    ) {
        require(status == WorkflowRunStatus.RUNNING || status == WorkflowRunStatus.PENDING) {
            "Cannot stop run. Current status: $status"
        }
        status = WorkflowRunStatus.STOPPING
        this.stoppedBy = stoppedBy
        this.stopReason = reason
        this.stoppedAt = LocalDateTime.now()
    }

    /**
     * 실행 중지 완료
     */
    fun completeStop() {
        require(status == WorkflowRunStatus.STOPPING) {
            "Cannot complete stop. Current status: $status"
        }
        status = WorkflowRunStatus.STOPPED
        endedAt = LocalDateTime.now()
    }

    // === Progress Update Methods ===

    /**
     * 테스트 진행 상황 업데이트
     */
    fun updateProgress(
        passed: Int,
        failed: Int,
        total: Int,
    ) {
        this.passedTests = passed
        this.failedTests = failed
        this.totalTests = total
    }

    // === Airflow Sync Methods ===

    /**
     * Airflow에서 동기화된 정보로 업데이트
     */
    fun updateFromAirflow(
        airflowState: String,
        airflowUrl: String?,
        startedAt: LocalDateTime?,
        endedAt: LocalDateTime?,
    ) {
        this.airflowState = airflowState
        this.airflowUrl = airflowUrl
        this.lastSyncedAt = LocalDateTime.now()
        this.status = mapAirflowState(airflowState)

        // Update times if provided and not already set
        if (startedAt != null && this.startedAt == null) {
            this.startedAt = startedAt
        }
        if (endedAt != null && this.endedAt == null) {
            this.endedAt = endedAt
        }
    }

    /**
     * Airflow 상태를 WorkflowRunStatus로 매핑
     */
    private fun mapAirflowState(airflowState: String): WorkflowRunStatus =
        when (airflowState.uppercase()) {
            "QUEUED" -> WorkflowRunStatus.PENDING
            "RUNNING" -> WorkflowRunStatus.RUNNING
            "SUCCESS" -> WorkflowRunStatus.SUCCESS
            "FAILED" -> WorkflowRunStatus.FAILED
            "UP_FOR_RETRY" -> WorkflowRunStatus.RUNNING
            "UPSTREAM_FAILED" -> WorkflowRunStatus.FAILED
            "SKIPPED" -> WorkflowRunStatus.SKIPPED
            else -> WorkflowRunStatus.UNKNOWN
        }

    // === Status Check Methods ===

    /**
     * 실행 중인지 확인
     */
    fun isRunning(): Boolean = status == WorkflowRunStatus.RUNNING

    /**
     * 대기 중인지 확인
     */
    fun isPending(): Boolean = status == WorkflowRunStatus.PENDING

    /**
     * 완료되었는지 확인 (성공)
     */
    fun isCompleted(): Boolean = status == WorkflowRunStatus.SUCCESS

    /**
     * 실패했는지 확인
     */
    fun isFailed(): Boolean = status == WorkflowRunStatus.FAILED

    /**
     * 중지되었는지 확인
     */
    fun isStopped(): Boolean = status == WorkflowRunStatus.STOPPED

    /**
     * 중지 중인지 확인
     */
    fun isStopping(): Boolean = status == WorkflowRunStatus.STOPPING

    /**
     * 타임아웃 되었는지 확인
     */
    fun isTimedOut(): Boolean = status == WorkflowRunStatus.TIMEOUT

    /**
     * 종료되었는지 확인 (성공, 실패, 중지, 스킵, 타임아웃 포함)
     */
    fun isFinished(): Boolean =
        status == WorkflowRunStatus.SUCCESS ||
            status == WorkflowRunStatus.FAILED ||
            status == WorkflowRunStatus.STOPPED ||
            status == WorkflowRunStatus.SKIPPED ||
            status == WorkflowRunStatus.TIMEOUT

    // === Utility Methods ===

    /**
     * 실행 시간 계산 (초)
     */
    fun getDurationSeconds(): Double? {
        val start = startedAt ?: return null
        val end = endedAt ?: LocalDateTime.now()
        return Duration.between(start, end).toMillis() / 1000.0
    }

    /**
     * 스케줄된 실행인지 확인
     */
    fun isScheduled(): Boolean = runType == WorkflowRunType.SCHEDULED

    /**
     * 수동 실행인지 확인
     */
    fun isManual(): Boolean = runType == WorkflowRunType.MANUAL

    /**
     * 백필 실행인지 확인
     */
    fun isBackfill(): Boolean = runType == WorkflowRunType.BACKFILL

    /**
     * Airflow에서 동기화되었는지 확인
     */
    fun isSyncedFromAirflow(): Boolean = airflowDagRunId != null && lastSyncedAt != null

    /**
     * 동기화가 오래되었는지 확인
     */
    fun isSyncStale(staleThresholdMinutes: Long = 60): Boolean {
        val lastSync = lastSyncedAt ?: return true
        return Duration.between(lastSync, LocalDateTime.now()).toMinutes() > staleThresholdMinutes
    }

    /**
     * 통과율 계산 (0.0 ~ 1.0)
     */
    fun getPassRate(): Double {
        if (totalTests == 0) return 0.0
        return passedTests.toDouble() / totalTests.toDouble()
    }

    /**
     * 모든 테스트가 통과했는지 확인
     */
    fun isAllTestsPassed(): Boolean = totalTests > 0 && passedTests == totalTests && failedTests == 0

    /**
     * 실행을 중지할 수 있는지 확인
     */
    fun canBeStopped(): Boolean = status == WorkflowRunStatus.RUNNING || status == WorkflowRunStatus.PENDING

    /**
     * 실행 중지 (v2.0 Workflow API)
     */
    fun stop(
        stoppedBy: String,
        reason: String? = null,
    ) {
        require(canBeStopped()) {
            "Cannot stop run. Current status: $status"
        }

        status = WorkflowRunStatus.STOPPING
        this.stoppedBy = stoppedBy
        this.stopReason = reason
        this.stoppedAt = LocalDateTime.now()
    }
}
