package com.dataops.basecamp.controller

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.domain.service.AirflowRunSyncService
import com.dataops.basecamp.domain.service.WorkflowSpecSyncService
import com.dataops.basecamp.dto.airflow.ClusterSyncResultDto
import com.dataops.basecamp.dto.airflow.RunSyncResultDto
import com.dataops.basecamp.dto.airflow.SpecSyncResultDto
import com.dataops.basecamp.mapper.AirflowSyncMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Airflow Sync Controller (Phase 6)
 *
 * Manual sync trigger endpoints for Airflow integration.
 * - S3 Spec sync: Syncs workflow YAML specs from S3 to DB
 * - DAG Run sync: Syncs DAG run statuses from Airflow clusters to DB
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/airflow/sync/manual")
@CrossOrigin
@Validated
@Tag(name = "Airflow Sync", description = "Manual Airflow sync API")
@PreAuthorize("hasRole('ROLE_ADMIN')")
class AirflowSyncController(
    private val specSyncService: WorkflowSpecSyncService,
    private val runSyncService: AirflowRunSyncService,
    private val mapper: AirflowSyncMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Trigger manual S3 Spec sync
     *
     * POST /api/v1/airflow/sync/manual/specs
     *
     * Syncs all YAML specs from S3 to the workflow database.
     * S3-first policy: existing workflows are overwritten with S3 content.
     */
    @Operation(
        summary = "Trigger S3 Spec sync",
        description = "Manually trigger sync of workflow YAML specs from S3 storage to database",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Sync completed")
    @SwaggerApiResponse(responseCode = "500", description = "Sync failed")
    @PostMapping("/specs")
    fun triggerSpecSync(): ResponseEntity<SpecSyncResultDto> {
        logger.info { "POST /api/v1/airflow/sync/manual/specs - Triggering S3 spec sync" }

        val result = specSyncService.syncFromStorage()
        val response = mapper.toSpecSyncResultDto(result)

        logger.info { "S3 spec sync completed: ${result.summary()}" }

        return ResponseEntity.ok(response)
    }

    /**
     * Trigger manual DAG Run sync for all active clusters
     *
     * POST /api/v1/airflow/sync/manual/runs
     *
     * Syncs DAG run statuses from all active Airflow clusters.
     */
    @Operation(
        summary = "Trigger DAG Run sync (all clusters)",
        description = "Manually trigger sync of DAG run statuses from all active Airflow clusters",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Sync completed")
    @SwaggerApiResponse(responseCode = "500", description = "Sync failed")
    @PostMapping("/runs")
    fun triggerRunSync(
        @Parameter(description = "Lookback hours for sync (default: 24)")
        @RequestParam(defaultValue = "24")
        @Min(1) lookbackHours: Long,
        @Parameter(description = "Max runs per cluster (default: 100)")
        @RequestParam(defaultValue = "100")
        @Min(1) batchSize: Int,
    ): ResponseEntity<RunSyncResultDto> {
        logger.info {
            "POST /api/v1/airflow/sync/manual/runs - Triggering DAG run sync for all clusters " +
                "(lookback: ${lookbackHours}h, batch: $batchSize)"
        }

        val result = runSyncService.syncAllClusters(lookbackHours, batchSize)
        val response = mapper.toRunSyncResultDto(result)

        logger.info {
            "DAG run sync completed: ${result.totalClusters} clusters, " +
                "${result.totalUpdated} updated, ${result.totalCreated} created, ${result.failedClusters} failed"
        }

        return ResponseEntity.ok(response)
    }

    /**
     * Trigger manual DAG Run sync for a specific cluster
     *
     * POST /api/v1/airflow/sync/manual/runs/cluster/{clusterId}
     *
     * Syncs DAG run statuses from a specific Airflow cluster.
     */
    @Operation(
        summary = "Trigger DAG Run sync (specific cluster)",
        description = "Manually trigger sync of DAG run statuses from a specific Airflow cluster",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Sync completed")
    @SwaggerApiResponse(responseCode = "404", description = "Cluster not found")
    @SwaggerApiResponse(responseCode = "500", description = "Sync failed")
    @PostMapping("/runs/cluster/{clusterId}")
    fun triggerClusterRunSync(
        @Parameter(description = "Airflow cluster ID")
        @PathVariable clusterId: Long,
        @Parameter(description = "Lookback hours for sync (default: 24)")
        @RequestParam(defaultValue = "24")
        @Min(1) lookbackHours: Long,
        @Parameter(description = "Max runs to sync (default: 100)")
        @RequestParam(defaultValue = "100")
        @Min(1) batchSize: Int,
    ): ResponseEntity<ClusterSyncResultDto> {
        logger.info {
            "POST /api/v1/airflow/sync/manual/runs/cluster/$clusterId - Triggering DAG run sync " +
                "(lookback: ${lookbackHours}h, batch: $batchSize)"
        }

        val result = runSyncService.syncCluster(clusterId, lookbackHours, batchSize)
        val response = mapper.toClusterSyncResultDto(result)

        logger.info {
            "DAG run sync for cluster $clusterId completed: " +
                "${result.updatedCount} updated, ${result.createdCount} created, error: ${result.error ?: "none"}"
        }

        return ResponseEntity.ok(response)
    }

    /**
     * Trigger sync for stale runs
     *
     * POST /api/v1/airflow/sync/manual/runs/stale
     *
     * Syncs runs that haven't been updated within the stale threshold.
     */
    @Operation(
        summary = "Trigger stale runs sync",
        description = "Sync runs that haven't been updated within the stale threshold",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Sync completed")
    @PostMapping("/runs/stale")
    fun triggerStaleRunsSync(
        @Parameter(description = "Stale threshold in hours (default: 1)")
        @RequestParam(defaultValue = "1")
        @Min(1) staleThresholdHours: Long,
    ): ResponseEntity<RunSyncResultDto> {
        logger.info {
            "POST /api/v1/airflow/sync/manual/runs/stale - Triggering stale runs sync " +
                "(threshold: ${staleThresholdHours}h)"
        }

        val result = runSyncService.syncStaleRuns(staleThresholdHours)
        val response = mapper.toRunSyncResultDto(result)

        logger.info {
            "Stale runs sync completed: ${result.totalUpdated} updated, ${result.failedClusters} failed"
        }

        return ResponseEntity.ok(response)
    }
}
