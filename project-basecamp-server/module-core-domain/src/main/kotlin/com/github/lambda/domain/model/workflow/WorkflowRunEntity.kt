package com.github.lambda.domain.model.workflow

import com.github.lambda.domain.model.BaseEntity
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
     * 종료되었는지 확인 (성공, 실패, 중지 포함)
     */
    fun isFinished(): Boolean =
        status == WorkflowRunStatus.SUCCESS ||
            status == WorkflowRunStatus.FAILED ||
            status == WorkflowRunStatus.STOPPED

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
}
