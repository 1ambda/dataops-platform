package com.github.lambda.domain.entity.workflow

import com.github.lambda.domain.entity.BaseEntity
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
 * Workflow Run Entity
 *
 * Workflow의 개별 실행 이력을 관리하는 엔티티
 */
@Entity
@Table(
    name = "workflow_runs",
    indexes = [
        Index(name = "idx_workflow_runs_run_id", columnList = "run_id", unique = true),
        Index(name = "idx_workflow_runs_workflow_id", columnList = "workflow_id"),
        Index(name = "idx_workflow_runs_dataset_name", columnList = "dataset_name"),
        Index(name = "idx_workflow_runs_status", columnList = "status"),
        Index(name = "idx_workflow_runs_run_type", columnList = "run_type"),
        Index(name = "idx_workflow_runs_triggered_by", columnList = "triggered_by"),
        Index(name = "idx_workflow_runs_started_at", columnList = "started_at"),
        // Airflow sync indexes
        Index(name = "idx_workflow_runs_airflow_dag_run_id", columnList = "airflow_dag_run_id"),
        Index(name = "idx_workflow_runs_airflow_cluster_id", columnList = "airflow_cluster_id"),
        Index(name = "idx_workflow_runs_last_synced_at", columnList = "last_synced_at"),
    ],
)
class WorkflowRunEntity(
    @NotBlank(message = "Run ID is required")
    @Size(max = 255, message = "Run ID must not exceed 255 characters")
    @Column(name = "run_id", nullable = false, unique = true, length = 255)
    var runId: String = "",
    @NotBlank(message = "Dataset name is required")
    @Size(max = 255, message = "Dataset name must not exceed 255 characters")
    @Column(name = "dataset_name", nullable = false, length = 255)
    var datasetName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: WorkflowRunStatus = WorkflowRunStatus.PENDING,
    @NotBlank(message = "Triggered by is required")
    @Size(max = 100, message = "Triggered by must not exceed 100 characters")
    @Column(name = "triggered_by", nullable = false, length = 100)
    var triggeredBy: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    var runType: WorkflowRunType = WorkflowRunType.MANUAL,
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null,
    @Column(name = "params", columnDefinition = "TEXT")
    var params: String? = null, // JSON string
    @Size(max = 500, message = "Logs URL must not exceed 500 characters")
    @Column(name = "logs_url", length = 500)
    var logsUrl: String? = null,
    @Size(max = 1000, message = "Stop reason must not exceed 1000 characters")
    @Column(name = "stop_reason", length = 1000)
    var stopReason: String? = null,
    @Size(max = 100, message = "Stopped by must not exceed 100 characters")
    @Column(name = "stopped_by", length = 100)
    var stoppedBy: String? = null,
    @Column(name = "stopped_at")
    var stoppedAt: LocalDateTime? = null,
    /**
     * Workflow ID (FK - references WorkflowEntity.datasetName)
     * Note: datasetName field also stores this value for denormalization
     */
    @Column(name = "workflow_id", nullable = false, length = 255)
    var workflowId: String = "",
    /**
     * Airflow의 실제 DAG Run ID (Phase 5)
     */
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
     * Airflow UI에서 해당 Run을 볼 수 있는 URL
     */
    @Size(max = 1000, message = "Airflow URL must not exceed 1000 characters")
    @Column(name = "airflow_url", length = 1000)
    var airflowUrl: String? = null,
    /**
     * 마지막으로 Airflow에서 동기화된 시간
     */
    @Column(name = "last_synced_at")
    var lastSyncedAt: LocalDateTime? = null,
    /**
     * Airflow 클러스터 ID (FK - references AirflowClusterEntity.id)
     */
    @Column(name = "airflow_cluster_id")
    var airflowClusterId: Long? = null,
    /**
     * Task 진행 상황 (JSON)
     */
    @Column(name = "task_progress", columnDefinition = "TEXT")
    var taskProgress: String? = null,
) : BaseEntity() {
    /**
     * 실행 시작
     */
    fun start() {
        if (status != WorkflowRunStatus.PENDING) {
            throw IllegalStateException("Cannot start run. Current status: $status")
        }
        status = WorkflowRunStatus.RUNNING
        startedAt = LocalDateTime.now()
    }

    /**
     * 실행 완료 (성공)
     */
    fun complete() {
        if (status != WorkflowRunStatus.RUNNING) {
            throw IllegalStateException("Cannot complete run. Current status: $status")
        }
        status = WorkflowRunStatus.SUCCESS
        endedAt = LocalDateTime.now()
    }

    /**
     * 실행 실패
     */
    fun fail() {
        if (status == WorkflowRunStatus.SUCCESS || status == WorkflowRunStatus.STOPPED) {
            throw IllegalStateException("Cannot fail run. Current status: $status")
        }
        status = WorkflowRunStatus.FAILED
        endedAt = LocalDateTime.now()
    }

    /**
     * 실행 중지 요청
     */
    fun requestStop(
        stoppedBy: String,
        reason: String? = null,
    ) {
        if (status != WorkflowRunStatus.RUNNING && status != WorkflowRunStatus.PENDING) {
            throw IllegalStateException("Cannot stop run. Current status: $status")
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
        if (status != WorkflowRunStatus.STOPPING) {
            throw IllegalStateException("Cannot complete stop. Current status: $status")
        }
        status = WorkflowRunStatus.STOPPED
        endedAt = LocalDateTime.now()
    }

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
     * 종료되었는지 확인 (성공, 실패, 중지, 스킵 포함)
     */
    fun isFinished(): Boolean =
        status == WorkflowRunStatus.SUCCESS ||
            status == WorkflowRunStatus.FAILED ||
            status == WorkflowRunStatus.STOPPED ||
            status == WorkflowRunStatus.SKIPPED

    /**
     * 실행 시간 계산 (초)
     */
    fun getDurationSeconds(): Double? {
        val start = startedAt ?: return null
        val end = endedAt ?: LocalDateTime.now()
        return java.time.Duration
            .between(start, end)
            .toMillis() / 1000.0
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

    // === Airflow Sync 메서드 (Phase 5) ===

    /**
     * Airflow에서 동기화된 정보로 업데이트
     *
     * @param airflowState Airflow의 원본 상태값
     * @param airflowUrl Airflow UI URL
     * @param taskProgress Task 진행 상황 (JSON)
     * @param startedAt 실제 시작 시간
     * @param endedAt 종료 시간
     */
    fun updateFromAirflow(
        airflowState: String,
        airflowUrl: String?,
        taskProgress: String?,
        startedAt: LocalDateTime?,
        endedAt: LocalDateTime?,
    ) {
        this.airflowState = airflowState
        this.airflowUrl = airflowUrl
        this.taskProgress = taskProgress
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
     * Airflow 상태를 Workflow Run 상태로 매핑
     *
     * @param airflowState Airflow 상태 문자열
     * @return 매핑된 WorkflowRunStatus
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

    /**
     * Airflow에서 동기화되었는지 확인
     */
    fun isSyncedFromAirflow(): Boolean = airflowDagRunId != null && lastSyncedAt != null

    /**
     * 동기화가 오래되었는지 확인 (staleThresholdMinutes 이상 경과)
     */
    fun isSyncStale(staleThresholdMinutes: Long = 60): Boolean {
        val lastSync = lastSyncedAt ?: return true
        return java.time.Duration
            .between(lastSync, LocalDateTime.now())
            .toMinutes() > staleThresholdMinutes
    }
}
