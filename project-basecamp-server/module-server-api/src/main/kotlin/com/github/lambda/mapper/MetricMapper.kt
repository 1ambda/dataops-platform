package com.github.lambda.mapper

import com.github.lambda.domain.entity.metric.MetricEntity
import com.github.lambda.domain.projection.metric.MetricExecutionProjection
import com.github.lambda.dto.metric.CreateMetricRequest
import com.github.lambda.dto.metric.MetricExecutionResultDto
import com.github.lambda.dto.metric.MetricResponse
import org.springframework.stereotype.Component

/**
 * Metric Mapper
 *
 * Handles conversions between API DTOs and Domain entities.
 * - Request DTO -> Service parameters
 * - Domain Entity -> Response DTO
 */
@Component
class MetricMapper {
    /**
     * Convert MetricEntity to MetricResponse (full details)
     *
     * Used for GET /api/v1/metrics/{name}
     */
    fun toResponse(entity: MetricEntity): MetricResponse =
        MetricResponse(
            name = entity.name,
            type = "Metric",
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            tags = entity.tags.sorted(),
            sql = entity.sql,
            sourceTable = entity.sourceTable,
            dependencies = entity.dependencies.sorted(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Convert MetricEntity to MetricResponse (list view - no SQL)
     *
     * Used for GET /api/v1/metrics (list)
     */
    fun toListResponse(entity: MetricEntity): MetricResponse =
        MetricResponse(
            name = entity.name,
            type = "Metric",
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            tags = entity.tags.sorted(),
            sql = null, // Exclude SQL from list view
            sourceTable = entity.sourceTable,
            dependencies = null, // Exclude dependencies from list view
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Convert MetricExecutionProjection to MetricExecutionResultDto
     */
    fun toExecutionResultDto(result: MetricExecutionProjection): MetricExecutionResultDto =
        MetricExecutionResultDto(
            rows = result.rows,
            rowCount = result.rowCount,
            durationSeconds = result.durationSeconds,
            renderedSql = result.renderedSql,
        )

    /**
     * Extract parameters from CreateMetricRequest
     */
    fun extractCreateParams(request: CreateMetricRequest): CreateMetricParams =
        CreateMetricParams(
            name = request.name,
            owner = request.owner,
            team = request.team,
            description = request.description,
            sql = request.sql,
            sourceTable = request.sourceTable,
            tags = request.tags,
        )
}

/**
 * Parameters for creating a metric
 */
data class CreateMetricParams(
    val name: String,
    val owner: String,
    val team: String?,
    val description: String?,
    val sql: String,
    val sourceTable: String?,
    val tags: List<String>,
)
