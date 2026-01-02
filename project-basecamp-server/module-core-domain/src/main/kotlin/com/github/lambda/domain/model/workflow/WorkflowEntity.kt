package com.github.lambda.domain.model.workflow

import com.github.lambda.domain.model.BaseAuditableEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Workflow Entity
 *
 * 서버 기반 워크플로우 오케스트레이션을 관리하는 엔티티
 */
@Entity
@Table(
    name = "workflows",
    indexes = [
        Index(name = "idx_workflows_dataset_name", columnList = "dataset_name", unique = true),
        Index(name = "idx_workflows_source_type", columnList = "source_type"),
        Index(name = "idx_workflows_status", columnList = "status"),
        Index(name = "idx_workflows_owner", columnList = "owner"),
        Index(name = "idx_workflows_team", columnList = "team"),
        Index(name = "idx_workflows_airflow_dag_id", columnList = "airflow_dag_id"),
        Index(name = "idx_workflows_updated_at", columnList = "updated_at"),
    ],
)
class WorkflowEntity(
    @Id
    @NotBlank(message = "Dataset name is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+$",
        message = "Dataset name must follow pattern: catalog.schema.name",
    )
    @Size(max = 255, message = "Dataset name must not exceed 255 characters")
    @Column(name = "dataset_name", nullable = false, unique = true, length = 255)
    var datasetName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    var sourceType: WorkflowSourceType = WorkflowSourceType.MANUAL,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: WorkflowStatus = WorkflowStatus.ACTIVE,
    @NotBlank(message = "Owner is required")
    @Email(message = "Owner must be a valid email")
    @Size(max = 100, message = "Owner must not exceed 100 characters")
    @Column(name = "owner", nullable = false, length = 100)
    var owner: String = "",
    @Size(max = 100, message = "Team must not exceed 100 characters")
    @Column(name = "team", length = 100)
    var team: String? = null,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @NotBlank(message = "S3 path is required")
    @Size(max = 500, message = "S3 path must not exceed 500 characters")
    @Column(name = "s3_path", nullable = false, length = 500)
    var s3Path: String = "",
    @NotBlank(message = "Airflow DAG ID is required")
    @Size(max = 255, message = "Airflow DAG ID must not exceed 255 characters")
    @Column(name = "airflow_dag_id", nullable = false, length = 255)
    var airflowDagId: String = "",
    @Embedded
    var schedule: ScheduleInfo = ScheduleInfo(),
) : BaseAuditableEntity() {
    @OneToMany(
        mappedBy = "workflow",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    var runs: MutableList<WorkflowRunEntity> = mutableListOf()

    /**
     * 워크플로우가 활성 상태인지 확인
     */
    fun isActive(): Boolean = status == WorkflowStatus.ACTIVE

    /**
     * 워크플로우가 일시 정지 상태인지 확인
     */
    fun isPaused(): Boolean = status == WorkflowStatus.PAUSED

    /**
     * 워크플로우가 비활성 상태인지 확인
     */
    fun isDisabled(): Boolean = status == WorkflowStatus.DISABLED

    /**
     * 실행 가능한지 확인
     */
    fun canRun(): Boolean = isActive()

    /**
     * 수동으로 관리되는 워크플로우인지 확인
     */
    fun isManualSource(): Boolean = sourceType == WorkflowSourceType.MANUAL

    /**
     * 코드로 관리되는 워크플로우인지 확인
     */
    fun isCodeSource(): Boolean = sourceType == WorkflowSourceType.CODE

    /**
     * 워크플로우 일시 정지
     */
    fun pause() {
        if (!isActive()) {
            throw IllegalStateException("Cannot pause workflow. Current status: $status")
        }
        status = WorkflowStatus.PAUSED
    }

    /**
     * 워크플로우 재시작
     */
    fun unpause() {
        if (!isPaused()) {
            throw IllegalStateException("Cannot unpause workflow. Current status: $status")
        }
        status = WorkflowStatus.ACTIVE
    }

    /**
     * 워크플로우 비활성화
     */
    fun disable() {
        status = WorkflowStatus.DISABLED
    }

    /**
     * 실행 이력 추가
     */
    fun addRun(run: WorkflowRunEntity) {
        runs.add(run)
        run.workflow = this
    }

    /**
     * 가장 최근 실행 이력 조회
     */
    fun getLastRun(): WorkflowRunEntity? = runs.maxByOrNull { it.startedAt ?: java.time.LocalDateTime.MIN }

    /**
     * catalog 추출
     */
    fun getCatalog(): String {
        val parts = datasetName.split(".")
        return if (parts.size >= 2) parts[0] else ""
    }

    /**
     * schema 추출
     */
    fun getSchema(): String {
        val parts = datasetName.split(".")
        return if (parts.size >= 2) parts.getOrNull(1) ?: "" else ""
    }

    /**
     * dataset name 추출
     */
    fun getDatasetShortName(): String = datasetName.split(".").getOrNull(2) ?: datasetName
}
