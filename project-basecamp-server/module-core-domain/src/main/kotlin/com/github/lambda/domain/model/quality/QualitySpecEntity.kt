package com.github.lambda.domain.model.quality

import com.github.lambda.domain.model.BaseEntity
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
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
) : BaseEntity() {
    /**
     * 태그 업데이트
     */
    fun updateTags(newTags: Set<String>) {
        tags.clear()
        tags.addAll(newTags)
    }
}
