package com.github.lambda.api.dto.transpile

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Transpile API DTOs
 *
 * Data transfer objects for transpile API endpoints.
 */
data class TranspileRulesDto(
    val version: String,
    val rules: List<TranspileRuleDto>,
    val metadata: TranspileMetadataDto,
)

/**
 * Individual transpile rule DTO
 */
data class TranspileRuleDto(
    val name: String,
    @JsonProperty("from_dialect")
    val fromDialect: String,
    @JsonProperty("to_dialect")
    val toDialect: String,
    val pattern: String,
    val replacement: String,
    val priority: Int,
    val enabled: Boolean,
    val description: String?,
)

/**
 * Metadata for transpile rules response
 */
data class TranspileMetadataDto(
    @JsonProperty("created_at")
    val createdAt: Instant,
    @JsonProperty("created_by")
    val createdBy: String,
    @JsonProperty("total_rules")
    val totalRules: Int,
    @JsonProperty("cache_ttl_seconds")
    val cacheTtlSeconds: Int,
)

/**
 * Response DTO for transpile result (metric or dataset)
 */
data class TranspileResultDto(
    @JsonProperty("metric_name", required = false)
    val metricName: String? = null,
    @JsonProperty("dataset_name", required = false)
    val datasetName: String? = null,
    @JsonProperty("source_dialect")
    val sourceDialect: String,
    @JsonProperty("target_dialect")
    val targetDialect: String,
    @JsonProperty("original_sql")
    val originalSql: String,
    @JsonProperty("transpiled_sql")
    val transpiledSql: String,
    @JsonProperty("applied_rules")
    val appliedRules: List<AppliedRuleDto>,
    val warnings: List<TranspileWarningDto>,
    @JsonProperty("transpiled_at")
    val transpiledAt: Instant,
    @JsonProperty("duration_ms")
    val durationMs: Long,
)

/**
 * Applied rule information
 */
data class AppliedRuleDto(
    val name: String,
    val source: String,
    val target: String,
)

/**
 * Transpile warning information
 */
data class TranspileWarningDto(
    val type: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)

/**
 * Request DTO for parser transpile call
 */
data class ParserTranspileRequestDto(
    val sql: String,
    @JsonProperty("source_dialect")
    val sourceDialect: String,
    @JsonProperty("target_dialect")
    val targetDialect: String,
    val rules: List<ParserRuleDto>,
)

/**
 * Parser rule DTO for external API calls
 */
data class ParserRuleDto(
    val name: String,
    val pattern: String,
    val replacement: String,
)

/**
 * Response DTO from parser service
 */
data class ParserTranspileResponseDto(
    val success: Boolean,
    @JsonProperty("transpiled_sql")
    val transpiledSql: String,
    @JsonProperty("applied_transformations")
    val appliedTransformations: List<TransformationDto>,
    val warnings: List<ParserWarningDto>,
    @JsonProperty("parse_time_ms")
    val parseTimeMs: Long,
    @JsonProperty("transpile_time_ms")
    val transpileTimeMs: Long,
)

/**
 * Transformation DTO from parser service
 */
data class TransformationDto(
    val type: String,
    val name: String?,
    val from: String?,
    val to: String?,
)

/**
 * Warning DTO from parser service
 */
data class ParserWarningDto(
    val type: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)
