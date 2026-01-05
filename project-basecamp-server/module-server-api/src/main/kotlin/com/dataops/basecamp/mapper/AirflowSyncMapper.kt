package com.dataops.basecamp.mapper

import com.dataops.basecamp.domain.projection.workflow.ClusterSyncProjection
import com.dataops.basecamp.domain.projection.workflow.RunSyncProjection
import com.dataops.basecamp.domain.projection.workflow.SpecSyncErrorProjection
import com.dataops.basecamp.domain.projection.workflow.SpecSyncProjection
import com.dataops.basecamp.dto.airflow.ClusterSyncResultDto
import com.dataops.basecamp.dto.airflow.RunSyncResultDto
import com.dataops.basecamp.dto.airflow.SpecSyncErrorDto
import com.dataops.basecamp.dto.airflow.SpecSyncResultDto
import org.springframework.stereotype.Component

/**
 * AirflowSyncMapper (Phase 6)
 *
 * Maps between domain sync result models and API DTOs.
 */
@Component
class AirflowSyncMapper {
    /**
     * Map SpecSyncProjection to DTO
     */
    fun toSpecSyncResultDto(result: SpecSyncProjection): SpecSyncResultDto =
        SpecSyncResultDto(
            totalProcessed = result.totalProcessed,
            created = result.created,
            updated = result.updated,
            failed = result.failed,
            errors = result.errors.map { toSpecSyncErrorDto(it) },
            syncedAt = result.syncedAt,
            success = result.isSuccess(),
            summary = buildSpecSyncSummary(result),
        )

    /**
     * Map SpecSyncErrorProjection to DTO
     */
    fun toSpecSyncErrorDto(error: SpecSyncErrorProjection): SpecSyncErrorDto =
        SpecSyncErrorDto(
            specPath = error.specPath,
            message = error.message,
            errorType = error.errorType.name,
        )

    /**
     * Map RunSyncProjection to DTO
     */
    fun toRunSyncResultDto(result: RunSyncProjection): RunSyncResultDto =
        RunSyncResultDto(
            totalClusters = result.totalClusters,
            clusterResults = result.clusterResults.map { toClusterSyncResultDto(it) },
            syncedAt = result.syncedAt,
            totalUpdated = result.totalUpdated,
            totalCreated = result.totalCreated,
            failedClusters = result.failedClusters,
            success = result.isSuccess,
        )

    /**
     * Map ClusterSyncProjection to DTO
     */
    fun toClusterSyncResultDto(result: ClusterSyncProjection): ClusterSyncResultDto =
        ClusterSyncResultDto(
            clusterId = result.clusterId,
            clusterName = result.clusterName,
            updatedCount = result.updatedCount,
            createdCount = result.createdCount,
            totalProcessed = result.totalProcessed,
            error = result.error,
            success = result.isSuccess,
        )

    /**
     * Build a human-readable summary for spec sync
     */
    private fun buildSpecSyncSummary(result: SpecSyncProjection): String =
        buildString {
            append("Processed ${result.totalProcessed} specs: ")
            append("${result.created} created, ")
            append("${result.updated} updated, ")
            append("${result.failed} failed")
            if (result.errors.isNotEmpty()) {
                append(" (${result.errors.size} errors)")
            }
        }
}
