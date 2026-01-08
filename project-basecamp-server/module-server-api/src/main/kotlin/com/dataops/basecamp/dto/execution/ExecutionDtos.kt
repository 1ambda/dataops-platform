package com.dataops.basecamp.dto.execution

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/**
 * Dataset Execution Request DTO
 *
 * CLI에서 렌더링된 SQL을 받아 실행하는 요청입니다.
 * Flat + Prefix 스타일의 DTO입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DatasetExecutionRequest(
    // SQL (required)
    @field:NotBlank(message = "Rendered SQL is required")
    @field:Size(max = 100000, message = "SQL must not exceed 100,000 characters")
    @JsonProperty("rendered_sql")
    val renderedSql: String,
    val parameters: Map<String, Any> = emptyMap(),
    // Execution options (with execution prefix)
    @JsonProperty("execution_timeout")
    @field:Min(1, message = "Execution timeout must be at least 1 second")
    @field:Max(3600, message = "Execution timeout must not exceed 3600 seconds")
    val executionTimeout: Int = 300,
    @JsonProperty("execution_limit")
    @field:Min(1, message = "Execution limit must be at least 1")
    @field:Max(10000, message = "Execution limit must not exceed 10,000 rows")
    val executionLimit: Int? = null,
    // Transpile metadata (with transpile prefix)
    @JsonProperty("transpile_source_dialect")
    val transpileSourceDialect: String? = null,
    @JsonProperty("transpile_target_dialect")
    val transpileTargetDialect: String? = null,
    @JsonProperty("transpile_used_server_policy")
    val transpileUsedServerPolicy: Boolean = false,
    // Origin info (optional for logging/audit)
    @JsonProperty("resource_name")
    val resourceName: String? = null,
    @JsonProperty("original_spec")
    val originalSpec: Map<String, Any>? = null,
)

/**
 * Metric Execution Request DTO
 *
 * CLI에서 렌더링된 Metric SQL을 받아 실행하는 요청입니다.
 * Dataset과 동일한 구조입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MetricExecutionRequest(
    @field:NotBlank(message = "Rendered SQL is required")
    @field:Size(max = 100000, message = "SQL must not exceed 100,000 characters")
    @JsonProperty("rendered_sql")
    val renderedSql: String,
    val parameters: Map<String, Any> = emptyMap(),
    @JsonProperty("execution_timeout")
    @field:Min(1, message = "Execution timeout must be at least 1 second")
    @field:Max(3600, message = "Execution timeout must not exceed 3600 seconds")
    val executionTimeout: Int = 300,
    @JsonProperty("execution_limit")
    @field:Min(1, message = "Execution limit must be at least 1")
    @field:Max(10000, message = "Execution limit must not exceed 10,000 rows")
    val executionLimit: Int? = null,
    @JsonProperty("transpile_source_dialect")
    val transpileSourceDialect: String? = null,
    @JsonProperty("transpile_target_dialect")
    val transpileTargetDialect: String? = null,
    @JsonProperty("transpile_used_server_policy")
    val transpileUsedServerPolicy: Boolean = false,
    @JsonProperty("resource_name")
    val resourceName: String? = null,
    @JsonProperty("original_spec")
    val originalSpec: Map<String, Any>? = null,
)

/**
 * SQL Execution Request DTO
 *
 * Ad-hoc SQL 실행 요청입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SqlExecutionRequest(
    @field:NotBlank(message = "SQL is required")
    @field:Size(max = 100000, message = "SQL must not exceed 100,000 characters")
    val sql: String,
    val parameters: Map<String, Any> = emptyMap(),
    @JsonProperty("execution_timeout")
    @field:Min(1, message = "Execution timeout must be at least 1 second")
    @field:Max(3600, message = "Execution timeout must not exceed 3600 seconds")
    val executionTimeout: Int = 300,
    @JsonProperty("execution_limit")
    @field:Min(1, message = "Execution limit must be at least 1")
    @field:Max(10000, message = "Execution limit must not exceed 10,000 rows")
    val executionLimit: Int? = null,
    @JsonProperty("target_dialect")
    val targetDialect: String? = null,
)

/**
 * Quality Execution Request DTO
 *
 * CLI에서 렌더링된 Quality 테스트 SQL을 받아 실행하는 요청입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityExecutionRequest(
    @field:NotBlank(message = "Resource name is required")
    @JsonProperty("resource_name")
    val resourceName: String,
    @field:NotEmpty(message = "At least one test is required")
    @field:Valid
    val tests: List<QualityTestItemRequest>,
    @JsonProperty("execution_timeout")
    @field:Min(1, message = "Execution timeout must be at least 1 second")
    @field:Max(3600, message = "Execution timeout must not exceed 3600 seconds")
    val executionTimeout: Int = 300,
    @JsonProperty("transpile_source_dialect")
    val transpileSourceDialect: String? = null,
    @JsonProperty("transpile_target_dialect")
    val transpileTargetDialect: String? = null,
)

/**
 * Quality Test Item Request DTO
 *
 * 개별 Quality 테스트 항목입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityTestItemRequest(
    @field:NotBlank(message = "Test name is required")
    val name: String,
    @field:NotBlank(message = "Test type is required")
    val type: String, // not_null, unique, row_count, expression
    @field:NotBlank(message = "Rendered SQL is required")
    @JsonProperty("rendered_sql")
    val renderedSql: String,
)

/**
 * Execution Result Response DTO
 *
 * Dataset/Metric/SQL 실행 결과 응답입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecutionResultDto(
    @JsonProperty("execution_id")
    val executionId: String,
    val status: String, // COMPLETED, FAILED
    val rows: List<Map<String, Any?>>? = null,
    @JsonProperty("row_count")
    val rowCount: Int? = null,
    @JsonProperty("duration_seconds")
    val durationSeconds: Double? = null,
    @JsonProperty("rendered_sql")
    val renderedSql: String,
    val error: String? = null,
)

/**
 * Quality Execution Result Response DTO
 *
 * Quality 실행 결과 응답입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityExecutionResultDto(
    @JsonProperty("execution_id")
    val executionId: String,
    val status: String,
    val results: List<QualityTestResultDto>,
    @JsonProperty("total_tests")
    val totalTests: Int,
    @JsonProperty("passed_tests")
    val passedTests: Int,
    @JsonProperty("failed_tests")
    val failedTests: Int,
    @JsonProperty("total_duration_ms")
    val totalDurationMs: Long,
)

/**
 * Quality Test Result DTO
 *
 * 개별 Quality 테스트 결과입니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityTestResultDto(
    @JsonProperty("test_name")
    val testName: String,
    val passed: Boolean,
    @JsonProperty("failed_count")
    val failedCount: Int,
    @JsonProperty("failed_rows")
    val failedRows: List<Map<String, Any>>? = null,
    @JsonProperty("duration_ms")
    val durationMs: Long,
)
