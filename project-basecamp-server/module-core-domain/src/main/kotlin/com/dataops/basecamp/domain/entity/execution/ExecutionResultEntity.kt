package com.dataops.basecamp.domain.entity.execution

import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

/**
 * Execution Result Entity
 *
 * 실행 결과 데이터를 저장하는 엔티티
 *
 * Phase 1: Foundation
 * - 실행 결과 데이터 저장 (JSON 형식)
 * - 스키마 메타데이터 포함
 * - 결과 행 수 추적
 */
@Entity
@Table(
    name = "execution_result",
    indexes = [
        Index(name = "idx_execution_result_execution_id", columnList = "execution_id", unique = true),
    ],
)
class ExecutionResultEntity(
    /**
     * 실행 ID (ExecutionHistoryEntity의 executionId와 1:1 매핑)
     */
    @field:NotBlank(message = "Execution ID is required")
    @field:Size(max = 36, message = "Execution ID must not exceed 36 characters")
    @Column(name = "execution_id", nullable = false, unique = true, length = 36)
    val executionId: String,
    /**
     * 결과 데이터 (JSON 형식)
     * 형식: [{"column1": "value1", "column2": "value2"}, ...]
     */
    @Column(name = "result_data", nullable = false, columnDefinition = "JSON")
    val resultData: String,
    /**
     * 결과 행 수
     */
    @field:PositiveOrZero(message = "Row count must be positive or zero")
    @Column(name = "row_count", nullable = false)
    val rowCount: Int,
    /**
     * 결과 스키마 (JSON 형식)
     * 형식: [{"name": "column1", "type": "STRING"}, ...]
     */
    @Column(name = "schema", nullable = true, columnDefinition = "JSON")
    val schema: String? = null,
) : BaseEntity() {
    /**
     * 결과가 비어있는지 확인
     */
    fun isEmpty(): Boolean = rowCount == 0

    /**
     * 결과가 있는지 확인
     */
    fun hasResults(): Boolean = rowCount > 0
}
