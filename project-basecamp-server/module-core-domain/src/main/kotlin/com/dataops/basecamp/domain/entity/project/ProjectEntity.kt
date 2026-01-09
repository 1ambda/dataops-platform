package com.dataops.basecamp.domain.entity.project

import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Project Entity
 *
 * 프로젝트는 데이터 자산(Dataset, Metric, Quality Spec 등)을 그룹화하는 최상위 논리 단위입니다.
 * - name: 소문자, 하이픈 형식 (e.g., "marketing-analytics")
 * - displayName: 사람이 읽기 쉬운 이름
 * - 소프트 삭제(deletedAt)를 지원합니다.
 */
@Entity
@Table(
    name = "project",
    indexes = [
        Index(name = "idx_project_name", columnList = "name"),
        Index(name = "idx_project_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_project_name", columnNames = ["name"]),
    ],
)
class ProjectEntity(
    @field:NotBlank(message = "Project name is required")
    @field:Size(max = 100, message = "Project name must not exceed 100 characters")
    @field:Pattern(
        regexp = "^[a-z][a-z0-9-]*$",
        message = "Project name must be lowercase with hyphens only",
    )
    @Column(name = "name", nullable = false, unique = true, length = 100)
    var name: String,
    @field:NotBlank(message = "Display name is required")
    @field:Size(max = 200, message = "Display name must not exceed 200 characters")
    @Column(name = "display_name", nullable = false, length = 200)
    var displayName: String,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
) : BaseEntity() {
    /**
     * 프로젝트 정보를 업데이트합니다.
     * name은 변경할 수 없습니다.
     */
    fun update(
        displayName: String? = null,
        description: String? = null,
    ) {
        displayName?.let { this.displayName = it }
        description?.let { this.description = it }
    }
}
