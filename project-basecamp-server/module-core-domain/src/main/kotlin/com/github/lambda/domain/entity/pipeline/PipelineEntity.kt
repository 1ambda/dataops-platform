package com.github.lambda.domain.entity.pipeline

import com.github.lambda.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 데이터 파이프라인 엔티티
 *
 * JPA 엔티티로 설계되어 변경 가능한 상태를 가집니다.
 */
@Entity
@Table(
    name = "pipelines",
    indexes = [
        Index(name = "idx_pipeline_owner_active", columnList = "owner, is_active"),
        Index(name = "idx_pipeline_status_active", columnList = "status, is_active"),
        Index(name = "idx_pipeline_owner_status", columnList = "owner, status"),
    ],
)
class PipelineEntity(
    @NotBlank(message = "Pipeline name is required")
    @Size(max = 100, message = "Pipeline name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description", length = 500)
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PipelineStatus = PipelineStatus.DRAFT,
    @NotBlank(message = "Owner is required")
    @Column(name = "owner", nullable = false, length = 50)
    var owner: String = "",
    @Column(name = "schedule_expression", length = 200)
    var scheduleExpression: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "config", columnDefinition = "JSON")
    var config: String? = null,
) : BaseEntity() {
    /**
     * 파이프라인이 실행 가능한 상태인지 확인
     */
    fun isExecutable(): Boolean = isActive && status == PipelineStatus.ACTIVE && !isDeleted

    /**
     * 파이프라인 활성화
     */
    fun activate() {
        this.isActive = true
        this.status = PipelineStatus.ACTIVE
    }

    /**
     * 파이프라인 비활성화
     */
    fun deactivate() {
        this.isActive = false
        this.status = PipelineStatus.PAUSED
    }
}

/**
 * 파이프라인 상태
 */
enum class PipelineStatus {
    DRAFT, // 초안 상태
    ACTIVE, // 활성 (실행 가능)
    RUNNING, // 현재 실행 중
    PAUSED, // 일시 중지
    STOPPED, // 중단됨
    FAILED, // 실패
    SUCCESS, // 성공적으로 완료
    IDLE, // 대기 상태
}
