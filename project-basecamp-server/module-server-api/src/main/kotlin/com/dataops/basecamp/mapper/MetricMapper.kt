package com.dataops.basecamp.mapper

import com.dataops.basecamp.domain.command.metric.CreateMetricCommand
import com.dataops.basecamp.domain.entity.metric.MetricEntity
import com.dataops.basecamp.domain.projection.metric.MetricExecutionProjection
import com.dataops.basecamp.dto.metric.CreateMetricRequest
import com.dataops.basecamp.dto.metric.MetricExecutionResultDto
import com.dataops.basecamp.dto.metric.MetricResponse
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
     * Extract command from CreateMetricRequest
     */
    fun extractCreateCommand(request: CreateMetricRequest): CreateMetricCommand =
        CreateMetricCommand(
            name = request.name,
            owner = request.owner,
            team = request.team,
            description = request.description,
            sql = request.sql,
            sourceTable = request.sourceTable,
            tags = request.tags.toSet(), // Convert List to Set as expected by Command
        )
}
