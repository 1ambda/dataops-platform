package com.github.lambda.domain.entity.quality

import com.github.lambda.common.enums.QualitySpecStatus
import com.github.lambda.common.enums.ResourceType
import com.github.lambda.common.enums.WorkflowSourceType
import com.github.lambda.domain.entity.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * Quality Spec Entity
 *
 * 데이터 품질 테스트 명세를 관리하는 엔티티
 */
@Entity
@Table(
    name = "quality_specs",
    indexes = [
        Index(name = "idx_quality_specs_name", columnList = "name", unique = true),
        Index(name = "idx_quality_specs_resource_name", columnList = "resource_name"),
        Index(name = "idx_quality_specs_resource_type", columnList = "resource_type"),
        Index(name = "idx_quality_specs_owner", columnList = "owner"),
        Index(name = "idx_quality_specs_enabled", columnList = "enabled"),
        Index(name = "idx_quality_specs_status", columnList = "status"),
        Index(name = "idx_quality_specs_source_type", columnList = "source_type"),
        Index(name = "idx_quality_specs_updated_at", columnList = "updated_at"),
    ],
)
class QualitySpecEntity(
    @NotBlank(message = "Quality spec name is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9_.-]+$",
        message = "Name must contain only alphanumeric characters, hyphens, underscores, and dots",
    )
    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Column(name = "name", nullable = false, unique = true, length = 255)
    var name: String = "",
    @NotBlank(message = "Resource name is required")
    @Size(max = 255, message = "Resource name must not exceed 255 characters")
    @Column(name = "resource_name", nullable = false, length = 255)
    var resourceName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    var resourceType: ResourceType = ResourceType.DATASET,
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
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "quality_spec_tags",
        joinColumns = [JoinColumn(name = "quality_spec_id")],
    )
    @Column(name = "tag", length = 50)
    var tags: MutableSet<String> = mutableSetOf(),
    @Pattern(
        regexp = "^[0-9\\s\\*\\-\\,\\/]+$",
        message = "Schedule cron must be a valid cron expression",
    )
    @Size(max = 100, message = "Schedule cron must not exceed 100 characters")
    @Column(name = "schedule_cron", length = 100)
    var scheduleCron: String? = null,
    @Size(max = 50, message = "Schedule timezone must not exceed 50 characters")
    @Column(name = "schedule_timezone", nullable = false, length = 50)
    var scheduleTimezone: String = "UTC",
    /**
     * 레거시 enabled 필드 - 하위 호환성을 위해 유지
     * @deprecated Use status field instead
     */
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
    /** Quality Spec 상태 (enabled를 대체) - Workflow Integration v2.0 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: QualitySpecStatus = QualitySpecStatus.ACTIVE,
    /**
     * 일시정지한 사용자
     */
    @Size(max = 255, message = "Paused by must not exceed 255 characters")
    @Column(name = "paused_by", length = 255)
    var pausedBy: String? = null,
    /**
     * 일시정지 시간
     */
    @Column(name = "paused_at")
    var pausedAt: LocalDateTime? = null,
    /**
     * 일시정지 사유
     */
    @Size(max = 500, message = "Pause reason must not exceed 500 characters")
    @Column(name = "pause_reason", length = 500)
    var pauseReason: String? = null,
    /**
     * 소스 타입 (CODE: Git 기반, MANUAL: 수동 등록)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    var sourceType: WorkflowSourceType = WorkflowSourceType.MANUAL,
    /**
     * S3 저장 경로 (YAML 파일 위치)
     */
    @Size(max = 500, message = "S3 path must not exceed 500 characters")
    @Column(name = "s3_path", length = 500)
    var s3Path: String? = null,
    /**
     * Airflow DAG ID
     */
    @Size(max = 255, message = "Airflow DAG ID must not exceed 255 characters")
    @Column(name = "airflow_dag_id", length = 255)
    var airflowDagId: String? = null,
) : BaseEntity() {
    /**
     * 태그 업데이트
     */
    fun updateTags(newTags: Set<String>) {
        tags.clear()
        tags.addAll(newTags)
    }

    // === Workflow Status Management Methods (v2.0) ===

    /**
     * Quality Spec을 일시정지할 수 있는지 확인
     */
    fun canBePaused(): Boolean = status == QualitySpecStatus.ACTIVE

    /**
     * Quality Spec 일시정지
     *
     * @param by 일시정지를 요청한 사용자
     * @param reason 일시정지 사유
     */
    fun pause(
        by: String,
        reason: String? = null,
    ) {
        require(canBePaused()) {
            "Cannot pause quality spec. Current status: $status"
        }
        status = QualitySpecStatus.PAUSED
        pausedBy = by
        pausedAt = LocalDateTime.now()
        pauseReason = reason
        enabled = false // Keep in sync for backward compatibility
    }

    /**
     * Quality Spec의 일시정지를 해제할 수 있는지 확인
     */
    fun canBeUnpaused(): Boolean = status == QualitySpecStatus.PAUSED

    /**
     * Quality Spec 일시정지 해제
     */
    fun unpause() {
        require(canBeUnpaused()) {
            "Cannot unpause quality spec. Current status: $status"
        }
        status = QualitySpecStatus.ACTIVE
        pausedBy = null
        pausedAt = null
        pauseReason = null
        enabled = true // Keep in sync for backward compatibility
    }

    /**
     * Quality Spec 비활성화
     */
    fun disable() {
        status = QualitySpecStatus.DISABLED
        enabled = false // Keep in sync for backward compatibility
    }

    /**
     * 활성화 상태인지 확인
     */
    fun isActive(): Boolean = status == QualitySpecStatus.ACTIVE

    /**
     * 일시정지 상태인지 확인
     */
    fun isPaused(): Boolean = status == QualitySpecStatus.PAUSED

    /**
     * 비활성화 상태인지 확인
     */
    fun isDisabled(): Boolean = status == QualitySpecStatus.DISABLED

    /**
     * CODE 소스 타입인지 확인 (Git 기반)
     */
    fun isCodeSource(): Boolean = sourceType == WorkflowSourceType.CODE

    /**
     * MANUAL 소스 타입인지 확인 (수동 등록)
     */
    fun isManualSource(): Boolean = sourceType == WorkflowSourceType.MANUAL
}
