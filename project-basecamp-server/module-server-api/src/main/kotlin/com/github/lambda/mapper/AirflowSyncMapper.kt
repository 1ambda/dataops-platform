package com.github.lambda.mapper

import com.github.lambda.domain.model.workflow.ClusterSyncResult
import com.github.lambda.domain.model.workflow.RunSyncResult
import com.github.lambda.domain.model.workflow.SpecSyncError
import com.github.lambda.domain.model.workflow.SpecSyncResult
import com.github.lambda.dto.airflow.ClusterSyncResultDto
import com.github.lambda.dto.airflow.RunSyncResultDto
import com.github.lambda.dto.airflow.SpecSyncErrorDto
import com.github.lambda.dto.airflow.SpecSyncResultDto
import org.springframework.stereotype.Component

/**
 * AirflowSyncMapper (Phase 6)
 *
 * Maps between domain sync result models and API DTOs.
 */
@Component
class AirflowSyncMapper {
    /**
     * Map SpecSyncResult to DTO
     */
    fun toSpecSyncResultDto(result: SpecSyncResult): SpecSyncResultDto =
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
     * Map SpecSyncError to DTO
     */
    fun toSpecSyncErrorDto(error: SpecSyncError): SpecSyncErrorDto =
        SpecSyncErrorDto(
            specPath = error.specPath,
            message = error.message,
            errorType = error.errorType.name,
        )

    /**
     * Map RunSyncResult to DTO
     */
    fun toRunSyncResultDto(result: RunSyncResult): RunSyncResultDto =
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
     * Map ClusterSyncResult to DTO
     */
    fun toClusterSyncResultDto(result: ClusterSyncResult): ClusterSyncResultDto =
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
    private fun buildSpecSyncSummary(result: SpecSyncResult): String =
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
