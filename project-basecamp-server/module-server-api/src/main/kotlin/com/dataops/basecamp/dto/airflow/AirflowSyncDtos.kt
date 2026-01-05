package com.dataops.basecamp.dto.airflow

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * S3 Spec Sync Result DTO
 *
 * API response for workflow spec synchronization from S3.
 */
@Schema(description = "S3 Spec sync result")
data class SpecSyncResultDto(
    @Schema(description = "Total specs processed", example = "10")
    val totalProcessed: Int,
    @Schema(description = "Number of new workflows created", example = "3")
    val created: Int,
    @Schema(description = "Number of existing workflows updated", example = "5")
    val updated: Int,
    @Schema(description = "Number of failed specs", example = "2")
    val failed: Int,
    @Schema(description = "List of errors encountered during sync")
    val errors: List<SpecSyncErrorDto>,
    @Schema(description = "Sync timestamp")
    val syncedAt: Instant,
    @Schema(description = "Whether sync completed successfully (no errors)", example = "true")
    val success: Boolean,
    @Schema(description = "Summary message", example = "Processed 10 specs: 3 created, 5 updated, 2 failed")
    val summary: String,
)

/**
 * Spec Sync Error DTO
 */
@Schema(description = "Spec sync error details")
data class SpecSyncErrorDto(
    @Schema(
        description = "Path to the spec file that caused the error",
        example = "s3://bucket/workflows/my-workflow.yaml",
    )
    val specPath: String,
    @Schema(description = "Error message", example = "Invalid YAML syntax at line 15")
    val message: String,
    @Schema(description = "Error type", example = "PARSE_ERROR")
    val errorType: String,
)

/**
 * Run Sync Result DTO
 *
 * API response for DAG run synchronization from Airflow clusters.
 */
@Schema(description = "DAG Run sync result")
data class RunSyncResultDto(
    @Schema(description = "Total clusters synced", example = "3")
    val totalClusters: Int,
    @Schema(description = "Per-cluster sync results")
    val clusterResults: List<ClusterSyncResultDto>,
    @Schema(description = "Sync timestamp")
    val syncedAt: Instant,
    @Schema(description = "Total runs updated across all clusters", example = "50")
    val totalUpdated: Int,
    @Schema(description = "Total runs created across all clusters", example = "10")
    val totalCreated: Int,
    @Schema(description = "Number of clusters with sync failures", example = "0")
    val failedClusters: Int,
    @Schema(description = "Whether all clusters synced successfully", example = "true")
    val success: Boolean,
)

/**
 * Cluster Sync Result DTO
 */
@Schema(description = "Individual cluster sync result")
data class ClusterSyncResultDto(
    @Schema(description = "Cluster ID", example = "1")
    val clusterId: Long,
    @Schema(description = "Cluster name (team)", example = "data-engineering")
    val clusterName: String,
    @Schema(description = "Number of runs updated", example = "15")
    val updatedCount: Int,
    @Schema(description = "Number of runs created", example = "3")
    val createdCount: Int,
    @Schema(description = "Total runs processed", example = "18")
    val totalProcessed: Int,
    @Schema(description = "Error message if sync failed", example = "null")
    val error: String?,
    @Schema(description = "Whether sync succeeded", example = "true")
    val success: Boolean,
)

/**
 * Sync Statistics DTO
 */
@Schema(description = "Sync statistics")
data class SyncStatisticsDto(
    @Schema(description = "Total synced runs")
    val totalSyncedRuns: Long,
    @Schema(description = "Pending runs not yet synced")
    val pendingRuns: Long,
    @Schema(description = "Stale runs needing resync")
    val staleRuns: Long,
    @Schema(description = "Last successful sync time")
    val lastSyncTime: Instant?,
)
