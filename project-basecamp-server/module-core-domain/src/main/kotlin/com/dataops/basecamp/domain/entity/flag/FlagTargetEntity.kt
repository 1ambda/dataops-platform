package com.dataops.basecamp.domain.entity.flag

import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Feature Flag Target 엔티티
 *
 * 특정 사용자 또는 API 토큰에 대한 Flag Override 및 세부 Permission을 통합 관리합니다.
 * - enabled: Flag Override 활성화 여부
 * - permissions: JSON 형식의 세부 권한 (예: {"execute": true, "write": false})
 *
 * JPA 관계 어노테이션(@ManyToOne 등)을 사용하지 않고 FK 필드를 직접 관리합니다.
 */
@Entity
@Table(
    name = "flag_target",
    indexes = [
        Index(name = "idx_flag_target_subject", columnList = "subject_type, subject_id"),
        Index(name = "idx_flag_target_flag", columnList = "flag_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_flag_target",
            columnNames = ["flag_id", "subject_type", "subject_id"],
        ),
    ],
)
class FlagTargetEntity(
    // FK 필드 사용 (JPA 관계 어노테이션 금지 - ENTITY_RELATION.md 참조)
    @Column(name = "flag_id", nullable = false)
    var flagId: Long = 0L,
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    var subjectType: SubjectType = SubjectType.USER,
    @Column(name = "subject_id", nullable = false)
    var subjectId: Long = 0L,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
    // JSON format: {"execute": true, "write": false}
    @Column(name = "permissions", columnDefinition = "JSON")
    var permissions: String? = null,
) : BaseEntity()
