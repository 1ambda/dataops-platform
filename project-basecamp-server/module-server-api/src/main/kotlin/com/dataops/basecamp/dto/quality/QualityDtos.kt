package com.dataops.basecamp.dto.quality

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDateTime

/**
 * Execute Quality Test Request DTO
 *
 * Used for POST /api/v1/quality/test/{resource_name}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteQualityTestRequest(
    @JsonProperty("quality_spec_name")
    val qualitySpecName: String? = null,
    @JsonProperty("test_names")
    @field:Size(max = 50, message = "Maximum 50 test names allowed")
    val testNames: List<String> = emptyList(),
    @field:Min(value = 1, message = "Timeout must be at least 1 second")
    @field:Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    val timeout: Int = 300,
    @JsonProperty("executed_by")
    val executedBy: String? = null,
)

/**
 * Quality Spec Summary DTO
 *
 * Used for GET /api/v1/quality list response (minimal info)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualitySpecSummaryDto(
    val name: String,
    @JsonProperty("resource_name")
    val resourceName: String,
    @JsonProperty("resource_type")
    val resourceType: String,
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    @JsonProperty("schedule_cron")
    val scheduleCron: String?,
    @JsonProperty("schedule_timezone")
    val scheduleTimezone: String,
    val enabled: Boolean,
    @JsonProperty("test_count")
    val testCount: Int = 0,
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime?,
)

/**
 * Quality Spec Detail DTO
 *
 * Used for GET /api/v1/quality/{name} response (full details)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualitySpecDetailDto(
    val name: String,
    @JsonProperty("resource_name")
    val resourceName: String,
    @JsonProperty("resource_type")
    val resourceType: String,
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    @JsonProperty("schedule_cron")
    val scheduleCron: String?,
    @JsonProperty("schedule_timezone")
    val scheduleTimezone: String,
    val enabled: Boolean,
    val tests: List<QualityTestDto>,
    @JsonProperty("recent_runs")
    val recentRuns: List<QualityRunSummaryDto> = emptyList(),
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime?,
)

/**
 * Quality Test DTO
 *
 * Used in QualitySpecDetailDto
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityTestDto(
    val name: String,
    @JsonProperty("test_type")
    val testType: String,
    val description: String?,
    @JsonProperty("target_columns")
    val targetColumns: List<String>,
    val config: Map<String, Any>?,
    val enabled: Boolean,
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
)

/**
 * Quality Run Summary DTO
 *
 * Used in QualitySpecDetailDto and QualityRunResultDto
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityRunSummaryDto(
    @JsonProperty("run_id")
    val runId: String,
    @JsonProperty("resource_name")
    val resourceName: String,
    val status: String,
    @JsonProperty("overall_status")
    val overallStatus: String?,
    @JsonProperty("passed_tests")
    val passedTests: Int,
    @JsonProperty("failed_tests")
    val failedTests: Int,
    @JsonProperty("duration_seconds")
    val durationSeconds: Double?,
    @JsonProperty("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val startedAt: Instant,
    @JsonProperty("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val completedAt: Instant?,
    @JsonProperty("executed_by")
    val executedBy: String,
)

/**
 * Quality Run Result DTO
 *
 * Used for POST /api/v1/quality/test/{resource_name} response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityRunResultDto(
    @JsonProperty("run_id")
    val runId: String,
    @JsonProperty("resource_name")
    val resourceName: String,
    @JsonProperty("quality_spec_name")
    val qualitySpecName: String,
    val status: String,
    @JsonProperty("overall_status")
    val overallStatus: String?,
    @JsonProperty("passed_tests")
    val passedTests: Int,
    @JsonProperty("failed_tests")
    val failedTests: Int,
    @JsonProperty("total_tests")
    val totalTests: Int,
    @JsonProperty("duration_seconds")
    val durationSeconds: Double?,
    @JsonProperty("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val startedAt: Instant,
    @JsonProperty("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val completedAt: Instant?,
    @JsonProperty("executed_by")
    val executedBy: String,
    @JsonProperty("test_results")
    val testResults: List<TestResultSummaryDto> = emptyList(),
)

/**
 * Test Result Summary DTO
 *
 * Used in QualityRunResultDto
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TestResultSummaryDto(
    @JsonProperty("test_name")
    val testName: String,
    @JsonProperty("test_type")
    val testType: String,
    val status: String,
    @JsonProperty("failed_rows")
    val failedRows: Long?,
    @JsonProperty("total_rows")
    val totalRows: Long?,
    @JsonProperty("execution_time_seconds")
    val executionTimeSeconds: Double?,
    @JsonProperty("error_message")
    val errorMessage: String?,
)
