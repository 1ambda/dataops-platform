package com.dataops.basecamp.dto.dataset

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.Valid
import jakarta.validation.constraints.*

/**
 * Dataset 생성 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateDatasetRequest(
    @field:NotBlank(message = "Dataset name is required")
    @field:Pattern(
        regexp = "^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$",
        message = "Dataset name must follow pattern: catalog.schema.name",
    )
    val name: String,
    @field:NotBlank(message = "Owner email is required")
    @field:Email(message = "Owner must be a valid email")
    val owner: String,
    @field:Size(max = 100, message = "Team name must not exceed 100 characters")
    val team: String? = null,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
    @field:NotBlank(message = "SQL expression is required")
    val sql: String,
    @field:Size(max = 10, message = "Maximum 10 tags allowed")
    val tags: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    @field:Valid
    val schedule: ScheduleRequest? = null,
)

/**
 * Schedule 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScheduleRequest(
    @field:NotBlank(message = "Cron expression is required")
    val cron: String,
    val timezone: String = "UTC",
)

/**
 * Dataset 실행 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteDatasetRequest(
    val parameters: Map<String, Any> = emptyMap(),
    @field:Min(value = 1, message = "Limit must be at least 1")
    @field:Max(value = 10000, message = "Limit must not exceed 10,000")
    val limit: Int = 1000,
    @field:Min(value = 1, message = "Timeout must be at least 1 second")
    @field:Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    val timeout: Int = 600,
)

/**
 * Dataset 상세 응답 DTO (GET /api/v1/datasets/{name})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DatasetDto(
    val name: String,
    val type: String = "Dataset",
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val sql: String?, // Included only for detail view
    val dependencies: List<String>?,
    val schedule: ScheduleDto?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String, // ISO 8601 format
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: String,
)

/**
 * Dataset 목록 응답 DTO (GET /api/v1/datasets) - Simplified for list view
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DatasetListDto(
    val name: String,
    val type: String = "Dataset",
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: String,
)

/**
 * Schedule 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScheduleDto(
    val cron: String,
    val timezone: String,
)

/**
 * Dataset 실행 결과 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecutionResultDto(
    val rows: List<Map<String, Any>>,
    val rowCount: Int,
    val durationSeconds: Double,
    val renderedSql: String,
)

/**
 * Dataset 등록 성공 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DatasetRegistrationResponse(
    val message: String,
    val name: String,
)

/**
 * Error 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val error: ErrorBody,
) {
    constructor(code: String, message: String, details: Map<String, Any>) :
        this(ErrorBody(code, message, details))
}

/**
 * Error 상세 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, Any>,
)
