package com.dataops.basecamp.domain.entity.flag

import com.dataops.basecamp.common.enums.FlagStatus
import com.dataops.basecamp.common.enums.TargetingType
import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Feature Flag 엔티티
 *
 * Feature Flag의 기본 정의를 저장합니다.
 */
@Entity
@Table(
    name = "flag",
    indexes = [
        Index(name = "idx_flag_key", columnList = "flag_key", unique = true),
        Index(name = "idx_flag_status", columnList = "status"),
        Index(name = "idx_flag_targeting_type", columnList = "targeting_type"),
    ],
)
class FlagEntity(
    @field:NotBlank(message = "Flag key is required")
    @field:Pattern(
        regexp = "^[a-z0-9_.-]+$",
        message = "Flag key must contain only lowercase alphanumeric characters, hyphens, underscores, and dots",
    )
    @field:Size(max = 100, message = "Flag key must not exceed 100 characters")
    @Column(name = "flag_key", nullable = false, unique = true, length = 100)
    var flagKey: String = "",
    @field:NotBlank(message = "Flag name is required")
    @field:Size(max = 200, message = "Flag name must not exceed 200 characters")
    @Column(name = "name", nullable = false, length = 200)
    var name: String = "",
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: FlagStatus = FlagStatus.DISABLED,
    @Enumerated(EnumType.STRING)
    @Column(name = "targeting_type", nullable = false)
    var targetingType: TargetingType = TargetingType.GLOBAL,
) : BaseEntity()
