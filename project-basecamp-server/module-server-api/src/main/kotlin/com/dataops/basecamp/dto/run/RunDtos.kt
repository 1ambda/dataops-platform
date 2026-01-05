package com.dataops.basecamp.dto.run

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * Ad-Hoc SQL 실행 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecuteSqlRequest(
    @field:NotBlank(message = "SQL query is required")
    @field:Size(max = 100000, message = "SQL query must not exceed 100,000 characters")
    val sql: String,
    @field:Pattern(
        regexp = "^(bigquery|trino)$",
        message = "Engine must be 'bigquery' or 'trino'",
    )
    val engine: String = "bigquery",
    val parameters: Map<String, Any> = emptyMap(),
    @field:Pattern(
        regexp = "^(csv)?$",
        message = "Download format must be 'csv' or null",
    )
    val downloadFormat: String? = null,
    val dryRun: Boolean = false,
)

/**
 * 실행 정책 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecutionPolicyResponseDto(
    val maxQueryDurationSeconds: Int,
    val maxResultRows: Int,
    val maxResultSizeMb: Int,
    val allowedEngines: List<String>,
    val allowedFileTypes: List<String>,
    val maxFileSizeMb: Int,
    val rateLimits: RateLimitsResponseDto,
    val currentUsage: CurrentUsageResponseDto,
)

/**
 * Rate Limits 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RateLimitsResponseDto(
    val queriesPerHour: Int,
    val queriesPerDay: Int,
)

/**
 * 현재 사용량 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CurrentUsageResponseDto(
    val queriesToday: Int,
    val queriesThisHour: Int,
)

/**
 * Ad-Hoc 실행 결과 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecutionResultResponseDto(
    val queryId: String?,
    val status: String,
    val executionTimeSeconds: Double,
    val rowsReturned: Int,
    val bytesScanned: String?,
    val costUsd: BigDecimal?,
    val downloadUrls: Map<String, String>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val expiresAt: String?,
    val renderedSql: String,
)

/**
 * 실행 히스토리 목록 조회 요청
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecutionHistoryRequest(
    val status: String? = null,
    val engine: String? = null,
    val search: String? = null,
    @field:Min(1)
    @field:Max(100)
    val limit: Int = 20,
    @field:Min(0)
    val offset: Int = 0,
)

/**
 * 실행 히스토리 항목 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecutionHistoryItemDto(
    val queryId: String,
    val status: String,
    val engine: String,
    val executionTimeSeconds: Double?,
    val rowsReturned: Int?,
    val bytesScanned: String?,
    val costUsd: BigDecimal?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val expiresAt: String?,
    val canDownload: Boolean,
)

/**
 * 실행 상세 정보 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExecutionDetailDto(
    val queryId: String,
    val status: String,
    val engine: String,
    val sqlQuery: String,
    val renderedSql: String,
    val executionTimeSeconds: Double?,
    val rowsReturned: Int?,
    val bytesScanned: String?,
    val costUsd: BigDecimal?,
    val errorMessage: String?,
    val downloadUrls: Map<String, String>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val expiresAt: String?,
    val canDownload: Boolean,
)
