package com.github.lambda.domain.entity.quality

import com.fasterxml.jackson.databind.JsonNode
import com.github.lambda.common.enums.TestStatus
import com.github.lambda.common.enums.TestType
import com.github.lambda.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Test Result Entity
 *
 * Quality Run에 포함되는 개별 테스트 결과를 관리하는 엔티티
 */
@Entity
@Table(
    name = "test_results",
    indexes = [
        Index(name = "idx_test_results_run_id", columnList = "run_id"),
        Index(name = "idx_test_results_test_id", columnList = "test_id"),
        Index(name = "idx_test_results_test_name", columnList = "test_name"),
        Index(name = "idx_test_results_status", columnList = "status"),
        Index(name = "idx_test_results_test_type", columnList = "test_type"),
        Index(name = "idx_test_results_failed_rows", columnList = "failed_rows"),
    ],
)
class TestResultEntity(
    @NotBlank(message = "Test name is required")
    @Size(max = 255, message = "Test name must not exceed 255 characters")
    @Column(name = "test_name", nullable = false, length = 255)
    var testName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 20)
    var testType: TestType = TestType.NOT_NULL,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TestStatus = TestStatus.PASSED,
    @PositiveOrZero(message = "Failed rows must be positive or zero")
    @Column(name = "failed_rows", nullable = false)
    var failedRows: Long = 0L,
    @PositiveOrZero(message = "Total rows must be positive or zero")
    @Column(name = "total_rows", nullable = false)
    var totalRows: Long = 0L,
    @PositiveOrZero(message = "Execution time must be positive or zero")
    @Column(name = "execution_time_seconds", nullable = false)
    var executionTimeSeconds: Double = 0.0,
    @Size(max = 2000, message = "Error message must not exceed 2000 characters")
    @Column(name = "error_message", length = 2000)
    var errorMessage: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_failures", columnDefinition = "JSON")
    var sampleFailures: JsonNode? = null,
    @Column(name = "generated_sql", columnDefinition = "TEXT")
    var generatedSql: String? = null,
    /**
     * Quality Run ID (FK)
     */
    @Column(name = "run_id", nullable = false)
    var runId: Long = 0L,
    /**
     * Quality Test ID (FK, nullable)
     */
    @Column(name = "test_id", nullable = true)
    var testId: Long? = null,
) : BaseEntity() {
    /**
     * 테스트가 통과했는지 확인
     */
    fun isPassed(): Boolean = status == TestStatus.PASSED

    /**
     * 테스트가 실패했는지 확인
     */
    fun isFailed(): Boolean = status == TestStatus.FAILED

    /**
     * 테스트에서 에러가 발생했는지 확인
     */
    fun hasError(): Boolean = status == TestStatus.ERROR

    /**
     * 테스트가 스킵되었는지 확인
     */
    fun isSkipped(): Boolean = status == TestStatus.SKIPPED

    /**
     * 실패율 계산 (%)
     */
    fun getFailureRate(): Double =
        if (totalRows > 0) {
            (failedRows.toDouble() / totalRows.toDouble()) * 100.0
        } else {
            0.0
        }

    /**
     * 성공율 계산 (%)
     */
    fun getSuccessRate(): Double = 100.0 - getFailureRate()

    /**
     * 테스트 실행 성공 처리
     */
    fun markAsSuccess(
        failedRows: Long,
        totalRows: Long,
        executionTime: Double,
        sql: String?,
    ) {
        this.status = if (failedRows > 0) TestStatus.FAILED else TestStatus.PASSED
        this.failedRows = failedRows
        this.totalRows = totalRows
        this.executionTimeSeconds = executionTime
        this.generatedSql = sql
        this.errorMessage = null
    }

    /**
     * 테스트 실행 실패 처리
     */
    fun markAsError(
        errorMessage: String,
        executionTime: Double,
        sql: String?,
    ) {
        this.status = TestStatus.ERROR
        this.errorMessage = errorMessage
        this.executionTimeSeconds = executionTime
        this.generatedSql = sql
        this.failedRows = 0L
        this.totalRows = 0L
    }

    /**
     * 테스트 스킵 처리
     */
    fun markAsSkipped(reason: String) {
        this.status = TestStatus.SKIPPED
        this.errorMessage = reason
        this.executionTimeSeconds = 0.0
        this.failedRows = 0L
        this.totalRows = 0L
    }
}
