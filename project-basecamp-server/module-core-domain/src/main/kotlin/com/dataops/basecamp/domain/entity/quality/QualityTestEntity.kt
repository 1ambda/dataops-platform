package com.dataops.basecamp.domain.entity.quality

import com.dataops.basecamp.common.enums.Severity
import com.dataops.basecamp.common.enums.TestType
import com.dataops.basecamp.domain.entity.BaseEntity
import com.fasterxml.jackson.databind.JsonNode
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
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Quality Test Entity
 *
 * Quality Spec에 포함되는 개별 테스트를 관리하는 엔티티
 */
@Entity
@Table(
    name = "quality_tests",
    indexes = [
        Index(name = "idx_quality_tests_spec_id", columnList = "spec_id"),
        Index(name = "idx_quality_tests_name", columnList = "name"),
        Index(name = "idx_quality_tests_test_type", columnList = "test_type"),
        Index(name = "idx_quality_tests_enabled", columnList = "enabled"),
        Index(name = "idx_quality_tests_updated_at", columnList = "updated_at"),
    ],
)
class QualityTestEntity(
    @NotBlank(message = "Test name is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9_.-]+$",
        message = "Test name must contain only alphanumeric characters, hyphens, underscores, and dots",
    )
    @Size(max = 255, message = "Test name must not exceed 255 characters")
    @Column(name = "name", nullable = false, length = 255)
    var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 20)
    var testType: TestType = TestType.NOT_NULL,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "quality_test_columns",
        joinColumns = [JoinColumn(name = "quality_test_id")],
    )
    @Column(name = "column_name", length = 255)
    var targetColumns: MutableList<String> = mutableListOf(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "JSON")
    var config: JsonNode? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    var severity: Severity = Severity.ERROR,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
    /**
     * Quality Spec ID (FK)
     */
    @Column(name = "spec_id", nullable = false)
    var specId: Long = 0L,
) : BaseEntity() {
    /**
     * 컬럼 목록 업데이트
     */
    fun updateTargetColumns(newColumns: Collection<String>) {
        targetColumns.clear()
        targetColumns.addAll(newColumns)
    }

    /**
     * 단일 컬럼 설정 (기존 컬럼들을 대체)
     */
    fun setSingleColumn(columnName: String) {
        targetColumns.clear()
        targetColumns.add(columnName)
    }

    /**
     * 첫 번째 컬럼 반환 (단일 컬럼 테스트용)
     */
    fun getPrimaryColumn(): String? = targetColumns.firstOrNull()

    /**
     * 모든 컬럼 반환
     */
    fun getAllColumns(): List<String> = targetColumns.toList()

    /**
     * 컬럼이 설정되었는지 확인
     */
    fun hasColumns(): Boolean = targetColumns.isNotEmpty()
}
