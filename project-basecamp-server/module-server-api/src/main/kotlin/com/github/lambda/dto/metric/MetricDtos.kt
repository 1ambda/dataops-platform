package com.github.lambda.dto.metric

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * Create Metric Request DTO
 *
 * Used for POST /api/v1/metrics
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateMetricRequest(
    @field:NotBlank(message = "Name is required")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+$",
        message = "Name must follow pattern: catalog.schema.name",
    )
    val name: String,
    @field:NotBlank(message = "Owner is required")
    @field:Email(message = "Owner must be a valid email")
    val owner: String,
    val team: String? = null,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
    @field:NotBlank(message = "SQL is required")
    @field:Size(max = 10000, message = "SQL must not exceed 10000 characters")
    val sql: String,
    @JsonProperty("source_table")
    val sourceTable: String? = null,
    @field:Size(max = 10, message = "Maximum 10 tags allowed")
    val tags: List<String> = emptyList(),
)

/**
 * Run Metric Request DTO
 *
 * Used for POST /api/v1/metrics/{name}/run
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RunMetricRequest(
    val parameters: Map<String, Any> = emptyMap(),
    @field:Min(value = 1, message = "Limit must be at least 1")
    @field:Max(value = 10000, message = "Limit must not exceed 10000")
    val limit: Int? = null,
    @field:Min(value = 1, message = "Timeout must be at least 1 second")
    @field:Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    val timeout: Int = 300,
)

/**
 * Metric Response DTO
 *
 * Used for GET /api/v1/metrics and GET /api/v1/metrics/{name}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MetricResponse(
    val name: String,
    val type: String = "Metric",
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val sql: String?,
    @JsonProperty("source_table")
    val sourceTable: String?,
    val dependencies: List<String>?,
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime?,
)

/**
 * Create Metric Response DTO
 *
 * Used for POST /api/v1/metrics response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateMetricResponse(
    val message: String,
    val name: String,
)

/**
 * Metric Execution Result DTO
 *
 * Used for POST /api/v1/metrics/{name}/run response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MetricExecutionResultDto(
    val rows: List<Map<String, Any>>,
    @JsonProperty("row_count")
    val rowCount: Int,
    @JsonProperty("duration_seconds")
    val durationSeconds: Double,
    @JsonProperty("rendered_sql")
    val renderedSql: String,
)
