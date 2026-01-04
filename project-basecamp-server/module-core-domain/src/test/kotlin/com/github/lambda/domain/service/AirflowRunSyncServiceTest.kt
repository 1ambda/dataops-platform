package com.github.lambda.domain.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.lambda.common.enums.AirflowEnvironment
import com.github.lambda.common.enums.WorkflowRunStatus
import com.github.lambda.common.enums.WorkflowRunType
import com.github.lambda.domain.entity.workflow.AirflowClusterEntity
import com.github.lambda.domain.entity.workflow.WorkflowRunEntity
import com.github.lambda.domain.external.airflow.AirflowClient
import com.github.lambda.domain.external.airflow.AirflowDAGRunState
import com.github.lambda.domain.external.airflow.AirflowDAGRunStatusResponse
import com.github.lambda.domain.external.airflow.AirflowDagRunResponse
import com.github.lambda.domain.projection.workflow.WorkflowSyncStatisticsProjection
import com.github.lambda.domain.repository.airflow.AirflowClusterRepositoryJpa
import com.github.lambda.domain.repository.workflow.WorkflowRunRepositoryDsl
import com.github.lambda.domain.repository.workflow.WorkflowRunRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * AirflowRunSyncService Unit Tests
 *
 * Tests for Airflow DAG run synchronization service.
 */
@DisplayName("AirflowRunSyncService Unit Tests")
class AirflowRunSyncServiceTest {
    private val airflowClient: AirflowClient = mockk()
    private val clusterRepository: AirflowClusterRepositoryJpa = mockk()
    private val workflowRunRepositoryJpa: WorkflowRunRepositoryJpa = mockk()
    private val workflowRunRepositoryDsl: WorkflowRunRepositoryDsl = mockk()
    private val objectMapper = ObjectMapper()

    private lateinit var service: AirflowRunSyncService
    private lateinit var testCluster: AirflowClusterEntity
    private lateinit var testWorkflowRun: WorkflowRunEntity

    @BeforeEach
    fun setUp() {
        service =
            AirflowRunSyncService(
                airflowClient = airflowClient,
                clusterRepository = clusterRepository,
                workflowRunRepositoryJpa = workflowRunRepositoryJpa,
                workflowRunRepositoryDsl = workflowRunRepositoryDsl,
                objectMapper = objectMapper,
            )

        testCluster =
            AirflowClusterEntity(
                id = 1L,
                team = "data-platform",
                clusterName = "data-platform-airflow",
                airflowUrl = "https://airflow.example.com",
                environment = AirflowEnvironment.PRODUCTION,
                dagS3Path = "s3://bucket/dags",
                dagNamePrefix = "dp_",
                isActive = true,
                apiKey = "test-api-key",
                description = "Data Platform Airflow Cluster",
            )

        testWorkflowRun =
            WorkflowRunEntity(
                runId = "test_run_123",
                datasetName = "catalog.schema.dataset",
                status = WorkflowRunStatus.RUNNING,
                triggeredBy = "user@example.com",
                runType = WorkflowRunType.MANUAL,
                startedAt = LocalDateTime.now().minusMinutes(10),
                workflowId = "dag_catalog_schema_dataset",
                airflowDagRunId = "manual__2025-01-15T12:00:00",
                airflowClusterId = 1L,
            )
    }

    @Nested
    @DisplayName("syncAllClusters")
    inner class SyncAllClusters {
        @Test
        @DisplayName("should sync all active clusters successfully")
        fun `should sync all active clusters successfully`() {
            // Given
            val airflowRuns =
                listOf(
                    AirflowDagRunResponse(
                        dagId = "dag_catalog_schema_dataset",
                        dagRunId = "manual__2025-01-15T12:00:00",
                        state = AirflowDAGRunState.SUCCESS,
                        logicalDate = LocalDateTime.now(),
                        startDate = LocalDateTime.now().minusMinutes(10),
                        endDate = LocalDateTime.now(),
                    ),
                )

            every { clusterRepository.findAllActive() } returns listOf(testCluster)
            every { airflowClient.listRecentDagRuns(any(), any()) } returns airflowRuns
            every {
                workflowRunRepositoryDsl.findPendingRunsByCluster(1L, any())
            } returns listOf(testWorkflowRun)
            every {
                workflowRunRepositoryJpa.findByAirflowDagRunId("manual__2025-01-15T12:00:00")
            } returns testWorkflowRun
            every { airflowClient.getTaskInstances(any(), any()) } returns emptyList()
            every { workflowRunRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = service.syncAllClusters(lookbackHours = 24, batchSize = 100)

            // Then
            assertThat(result.totalClusters).isEqualTo(1)
            assertThat(result.totalUpdated).isEqualTo(1)
            assertThat(result.failedClusters).isEqualTo(0)
            assertThat(result.isSuccess).isTrue()

            verify(exactly = 1) { clusterRepository.findAllActive() }
            verify(exactly = 1) { airflowClient.listRecentDagRuns(any(), any()) }
        }

        @Test
        @DisplayName("should return empty result when no active clusters")
        fun `should return empty result when no active clusters`() {
            // Given
            every { clusterRepository.findAllActive() } returns emptyList()

            // When
            val result = service.syncAllClusters()

            // Then
            assertThat(result.totalClusters).isEqualTo(0)
            assertThat(result.clusterResults).isEmpty()
            assertThat(result.isSuccess).isTrue()
        }

        @Test
        @DisplayName("should handle cluster sync failure gracefully")
        fun `should handle cluster sync failure gracefully`() {
            // Given
            every { clusterRepository.findAllActive() } returns listOf(testCluster)
            every { airflowClient.listRecentDagRuns(any(), any()) } throws RuntimeException("Airflow API error")

            // When
            val result = service.syncAllClusters()

            // Then
            assertThat(result.totalClusters).isEqualTo(1)
            assertThat(result.failedClusters).isEqualTo(1)
            assertThat(result.isSuccess).isFalse()
            assertThat(result.clusterResults[0].error).contains("Airflow API error")
        }

        @Test
        @DisplayName("should sync multiple clusters independently")
        fun `should sync multiple clusters independently`() {
            // Given
            val cluster2 =
                AirflowClusterEntity(
                    id = 2L,
                    team = "analytics",
                    clusterName = "analytics-airflow",
                    airflowUrl = "https://airflow-analytics.example.com",
                    environment = AirflowEnvironment.PRODUCTION,
                    dagS3Path = "s3://bucket/analytics-dags",
                    dagNamePrefix = "an_",
                    isActive = true,
                    apiKey = "test-api-key-2",
                )

            every { clusterRepository.findAllActive() } returns listOf(testCluster, cluster2)
            every { airflowClient.listRecentDagRuns(any(), any()) } returns emptyList()
            every { workflowRunRepositoryDsl.findPendingRunsByCluster(any(), any()) } returns emptyList()

            // When
            val result = service.syncAllClusters()

            // Then
            assertThat(result.totalClusters).isEqualTo(2)
            assertThat(result.clusterResults).hasSize(2)
            assertThat(result.isSuccess).isTrue()
        }
    }

    @Nested
    @DisplayName("syncCluster")
    inner class SyncCluster {
        @Test
        @DisplayName("should sync specific cluster successfully")
        fun `should sync specific cluster successfully`() {
            // Given
            every { clusterRepository.findById(1L) } returns testCluster
            every { airflowClient.listRecentDagRuns(any(), any()) } returns emptyList()
            every { workflowRunRepositoryDsl.findPendingRunsByCluster(1L, any()) } returns emptyList()

            // When
            val result = service.syncCluster(clusterId = 1L, lookbackHours = 24, batchSize = 100)

            // Then
            assertThat(result.clusterId).isEqualTo(1L)
            assertThat(result.clusterName).isEqualTo("data-platform")
            assertThat(result.isSuccess).isTrue()
        }

        @Test
        @DisplayName("should return failure when cluster not found")
        fun `should return failure when cluster not found`() {
            // Given
            every { clusterRepository.findById(999L) } returns null

            // When
            val result = service.syncCluster(clusterId = 999L)

            // Then
            assertThat(result.clusterId).isEqualTo(999L)
            assertThat(result.clusterName).isEqualTo("unknown")
            assertThat(result.isSuccess).isFalse()
            assertThat(result.error).contains("Cluster not found")
        }

        @Test
        @DisplayName("should update workflow run status from Airflow")
        fun `should update workflow run status from Airflow`() {
            // Given
            val airflowRun =
                AirflowDagRunResponse(
                    dagId = "dag_catalog_schema_dataset",
                    dagRunId = "manual__2025-01-15T12:00:00",
                    state = AirflowDAGRunState.SUCCESS,
                    logicalDate = LocalDateTime.now(),
                    startDate = LocalDateTime.now().minusMinutes(10),
                    endDate = LocalDateTime.now(),
                )

            every { clusterRepository.findById(1L) } returns testCluster
            every { airflowClient.listRecentDagRuns(any(), any()) } returns listOf(airflowRun)
            every {
                workflowRunRepositoryDsl.findPendingRunsByCluster(1L, any())
            } returns listOf(testWorkflowRun)
            every {
                workflowRunRepositoryJpa.findByAirflowDagRunId("manual__2025-01-15T12:00:00")
            } returns testWorkflowRun
            every { airflowClient.getTaskInstances(any(), any()) } returns emptyList()
            every { workflowRunRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = service.syncCluster(clusterId = 1L)

            // Then
            assertThat(result.updatedCount).isEqualTo(1)
            assertThat(result.isSuccess).isTrue()

            // Verify run was updated
            verify(exactly = 1) { workflowRunRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("syncStaleRuns")
    inner class SyncStaleRuns {
        @Test
        @DisplayName("should sync stale runs successfully")
        fun `should sync stale runs successfully`() {
            // Given
            val staleRun =
                WorkflowRunEntity(
                    runId = "stale_run_123",
                    datasetName = "catalog.schema.dataset",
                    status = WorkflowRunStatus.RUNNING,
                    triggeredBy = "user@example.com",
                    runType = WorkflowRunType.MANUAL,
                    startedAt = LocalDateTime.now().minusHours(2),
                    workflowId = "dag_catalog_schema_dataset",
                    airflowDagRunId = "manual__2025-01-15T10:00:00",
                    airflowClusterId = 1L,
                )

            val airflowStatus =
                AirflowDAGRunStatusResponse(
                    dagRunId = "manual__2025-01-15T10:00:00",
                    state = AirflowDAGRunState.SUCCESS,
                    startDate = LocalDateTime.now().minusHours(2),
                    endDate = LocalDateTime.now().minusHours(1),
                    executionDate = LocalDateTime.now().minusHours(2),
                    logsUrl = "https://airflow.example.com/logs/dag_catalog_schema_dataset/manual__2025-01-15T10:00:00",
                )

            every { workflowRunRepositoryDsl.findStaleRuns(any()) } returns listOf(staleRun)
            every { clusterRepository.findById(1L) } returns testCluster
            every {
                airflowClient.getDAGRun(
                    "dag_catalog_schema_dataset",
                    "manual__2025-01-15T10:00:00",
                )
            } returns airflowStatus
            every { airflowClient.getTaskInstances(any(), any()) } returns emptyList()
            every { workflowRunRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = service.syncStaleRuns(staleThresholdHours = 1)

            // Then
            assertThat(result.totalUpdated).isEqualTo(1)
            verify(exactly = 1) { workflowRunRepositoryDsl.findStaleRuns(any()) }
        }

        @Test
        @DisplayName("should return empty result when no stale runs")
        fun `should return empty result when no stale runs`() {
            // Given
            every { workflowRunRepositoryDsl.findStaleRuns(any()) } returns emptyList()

            // When
            val result = service.syncStaleRuns()

            // Then
            assertThat(result.totalUpdated).isEqualTo(0)
            assertThat(result.totalCreated).isEqualTo(0)
        }

        @Test
        @DisplayName("should handle individual stale run sync failure gracefully")
        fun `should handle individual stale run sync failure gracefully`() {
            // Given
            val staleRun =
                WorkflowRunEntity(
                    runId = "stale_run_123",
                    datasetName = "catalog.schema.dataset",
                    status = WorkflowRunStatus.RUNNING,
                    triggeredBy = "user@example.com",
                    runType = WorkflowRunType.MANUAL,
                    startedAt = LocalDateTime.now().minusHours(2),
                    workflowId = "dag_catalog_schema_dataset",
                    airflowDagRunId = "manual__2025-01-15T10:00:00",
                    airflowClusterId = 1L,
                )

            every { workflowRunRepositoryDsl.findStaleRuns(any()) } returns listOf(staleRun)
            every { clusterRepository.findById(1L) } returns testCluster
            // syncSingleRun catches exceptions internally and returns false
            every { airflowClient.getDAGRun(any(), any()) } throws RuntimeException("Airflow API error")

            // When
            val result = service.syncStaleRuns()

            // Then - run sync failure is handled gracefully, not counted as updated
            assertThat(result.totalUpdated).isEqualTo(0)
            // No error is added because syncSingleRun catches exceptions internally
            assertThat(result.clusterResults).hasSize(1)
            assertThat(result.clusterResults[0].updatedCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("getSyncStatistics")
    inner class GetSyncStatistics {
        @Test
        @DisplayName("should return sync statistics for all clusters")
        fun `should return sync statistics for all clusters`() {
            // Given
            val stats =
                WorkflowSyncStatisticsProjection(
                    clusterId = null,
                    totalRuns = 100L,
                    syncedRuns = 95L,
                    pendingSyncRuns = 5L,
                    staleRuns = 0L,
                    lastSyncedAt = null,
                )
            every { workflowRunRepositoryDsl.getSyncStatistics(null) } returns stats

            // When
            val result = service.getSyncStatistics()

            // Then
            assertThat(result.totalRuns).isEqualTo(100L)
            assertThat(result.syncedRuns).isEqualTo(95L)
            assertThat(result.pendingSyncRuns).isEqualTo(5L)
        }

        @Test
        @DisplayName("should return sync statistics for specific cluster")
        fun `should return sync statistics for specific cluster`() {
            // Given
            val stats =
                WorkflowSyncStatisticsProjection(
                    clusterId = 1L,
                    totalRuns = 50L,
                    syncedRuns = 48L,
                    pendingSyncRuns = 2L,
                    staleRuns = 0L,
                    lastSyncedAt = null,
                )
            every { workflowRunRepositoryDsl.getSyncStatistics(1L) } returns stats

            // When
            val result = service.getSyncStatistics(clusterId = 1L)

            // Then
            assertThat(result.totalRuns).isEqualTo(50L)
        }
    }
}
