package com.github.lambda.infra.external

import com.github.lambda.common.enums.AirflowEnvironment
import com.github.lambda.common.exception.AirflowConnectionException
import com.github.lambda.domain.entity.workflow.AirflowClusterEntity
import com.github.lambda.domain.model.workflow.ScheduleInfo
import com.github.lambda.domain.repository.airflow.AirflowClusterRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * MockRestAirflowClient Unit Tests
 *
 * Tests for cluster-aware mock Airflow client.
 */
@DisplayName("MockRestAirflowClient Unit Tests")
class MockRestAirflowClientTest {
    private val clusterRepository: AirflowClusterRepositoryJpa = mockk()
    private lateinit var client: MockRestAirflowClient
    private lateinit var testCluster: AirflowClusterEntity

    @BeforeEach
    fun setUp() {
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
            )

        every { clusterRepository.findAllActive() } returns listOf(testCluster)
        every { clusterRepository.findByTeam(any()) } returns null
        every { clusterRepository.findByTeam("data-platform") } returns testCluster

        client = MockRestAirflowClient(clusterRepository)
    }

    @Nested
    @DisplayName("triggerDAGRun")
    inner class TriggerDAGRun {
        @Test
        @DisplayName("should trigger DAG run with cluster routing")
        fun `should trigger DAG run with cluster routing`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            val runId = "manual__2025-01-15T12:00:00"
            val conf = mapOf("date" to "2025-01-15")

            // When
            val result = client.triggerDAGRun(dagId, runId, conf)

            // Then
            assertThat(result).isEqualTo(runId)
        }

        @Test
        @DisplayName("should use default cluster when team not found")
        fun `should use default cluster when team not found`() {
            // Given
            val dagId = "dag_unknown-team__workflow"
            val runId = "manual__2025-01-15T12:00:00"

            // When
            val result = client.triggerDAGRun(dagId, runId, emptyMap())

            // Then
            assertThat(result).isEqualTo(runId)
        }
    }

    @Nested
    @DisplayName("getDAGRun")
    inner class GetDAGRun {
        @Test
        @DisplayName("should get DAG run status")
        fun `should get DAG run status`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            val runId = "manual__2025-01-15T12:00:00"
            client.triggerDAGRun(dagId, runId, emptyMap())

            // When
            val status = client.getDAGRun(dagId, runId)

            // Then
            assertThat(status.dagRunId).isEqualTo(runId)
            assertThat(status.state).isNotNull
        }

        @Test
        @DisplayName("should throw exception for non-existent run")
        fun `should throw exception for non-existent run`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            val runId = "non_existent_run"

            // When & Then
            assertThrows<AirflowConnectionException> {
                client.getDAGRun(dagId, runId)
            }
        }
    }

    @Nested
    @DisplayName("listRecentDagRuns")
    inner class ListRecentDagRuns {
        @Test
        @DisplayName("should list recent DAG runs")
        fun `should list recent DAG runs`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            client.triggerDAGRun(dagId, "run1", emptyMap())
            client.triggerDAGRun(dagId, "run2", emptyMap())
            client.triggerDAGRun(dagId, "run3", emptyMap())

            // When
            val now = java.time.LocalDateTime.now()
            val oneHourAgo = now.minusHours(1)
            val runs = client.listRecentDagRuns(since = oneHourAgo, limit = 10)

            // Then
            assertThat(runs).hasSize(3)
        }

        @Test
        @DisplayName("should return empty list when no runs exist")
        fun `should return empty list when no runs exist`() {
            // When
            val now = java.time.LocalDateTime.now()
            val oneHourAgo = now.minusHours(1)
            val runs = client.listRecentDagRuns(since = oneHourAgo, limit = 10)

            // Then
            assertThat(runs).isEmpty()
        }

        @Test
        @DisplayName("should respect limit parameter")
        fun `should respect limit parameter`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            repeat(10) { i ->
                client.triggerDAGRun(dagId, "run_$i", emptyMap())
            }

            // When
            val now = java.time.LocalDateTime.now()
            val oneHourAgo = now.minusHours(1)
            val runs = client.listRecentDagRuns(since = oneHourAgo, limit = 5)

            // Then
            assertThat(runs).hasSize(5)
        }
    }

    @Nested
    @DisplayName("createBackfill")
    inner class CreateBackfill {
        @Test
        @DisplayName("should create backfill successfully")
        fun `should create backfill successfully`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            val fromDate = "2025-01-01"
            val toDate = "2025-01-15"

            // First create the DAG
            client.createDAG("data-platform__my_workflow", ScheduleInfo(cron = "0 8 * * *"), "s3://bucket/dag.py")

            // When
            val backfillResponse = client.createBackfill(dagId, fromDate, toDate)

            // Then
            assertThat(backfillResponse.id).isNotBlank()
            assertThat(backfillResponse.dagId).isEqualTo(dagId)
            assertThat(backfillResponse.fromDate).isEqualTo(fromDate)
            assertThat(backfillResponse.toDate).isEqualTo(toDate)
        }
    }

    @Nested
    @DisplayName("getBackfillStatus")
    inner class GetBackfillStatus {
        @Test
        @DisplayName("should throw exception for unknown backfill")
        fun `should throw exception for unknown backfill`() {
            // Given
            val unknownBackfillId = "unknown-backfill-id"

            // When & Then
            assertThrows<AirflowConnectionException> {
                client.getBackfillStatus(unknownBackfillId)
            }
        }
    }

    @Nested
    @DisplayName("pauseDAG")
    inner class PauseDAG {
        @Test
        @DisplayName("should pause DAG successfully")
        fun `should pause DAG successfully`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"

            // When
            val result = client.pauseDAG(dagId, isPaused = true)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should unpause DAG successfully")
        fun `should unpause DAG successfully`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            client.pauseDAG(dagId, isPaused = true)

            // When
            val result = client.pauseDAG(dagId, isPaused = false)

            // Then
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("stopDAGRun")
    inner class StopDAGRun {
        @Test
        @DisplayName("should stop running DAG run")
        fun `should stop running DAG run`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            val runId = "manual__2025-01-15T12:00:00"
            client.triggerDAGRun(dagId, runId, emptyMap())

            // When
            val result = client.stopDAGRun(dagId, runId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false for non-existent run")
        fun `should return false for non-existent run`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            val runId = "non_existent_run"

            // When
            val result = client.stopDAGRun(dagId, runId)

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("createDAG")
    inner class CreateDAG {
        @Test
        @DisplayName("should create DAG successfully")
        fun `should create DAG successfully`() {
            // Given
            val datasetName = "data-platform__my_workflow"
            val schedule = ScheduleInfo(cron = "0 8 * * *", timezone = "UTC")
            val s3Path = "s3://bucket/workflows/workflow.yaml"

            // When
            val dagId = client.createDAG(datasetName, schedule, s3Path)

            // Then
            assertThat(dagId).isNotBlank()
            assertThat(dagId).contains("dag_")
        }
    }

    @Nested
    @DisplayName("deleteDAG")
    inner class DeleteDAG {
        @Test
        @DisplayName("should delete existing DAG")
        fun `should delete existing DAG`() {
            // Given
            val datasetName = "data-platform__my_workflow"
            val schedule = ScheduleInfo(cron = "0 8 * * *", timezone = "UTC")
            val s3Path = "s3://bucket/workflows/workflow.yaml"
            val dagId = client.createDAG(datasetName, schedule, s3Path)

            // When
            val result = client.deleteDAG(dagId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false for non-existent DAG")
        fun `should return false for non-existent DAG`() {
            // Given
            val dagId = "non_existent_dag"

            // When
            val result = client.deleteDAG(dagId)

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("getDagStatus")
    inner class GetDagStatus {
        @Test
        @DisplayName("should get DAG status for existing DAG")
        fun `should get DAG status for existing DAG`() {
            // Given
            val datasetName = "data-platform__my_workflow"
            val schedule = ScheduleInfo(cron = "0 8 * * *", timezone = "UTC")
            val s3Path = "s3://bucket/workflows/workflow.yaml"
            val dagId = client.createDAG(datasetName, schedule, s3Path)

            // When
            val status = client.getDagStatus(dagId)

            // Then
            assertThat(status.dagId).isEqualTo(dagId)
            assertThat(status.isActive).isTrue()
        }

        @Test
        @DisplayName("should throw exception for non-existent DAG")
        fun `should throw exception for non-existent DAG`() {
            // Given
            val dagId = "non_existent_dag"

            // When & Then
            assertThrows<AirflowConnectionException> {
                client.getDagStatus(dagId)
            }
        }
    }

    @Nested
    @DisplayName("isAvailable")
    inner class IsAvailable {
        @Test
        @DisplayName("should return true when active clusters exist")
        fun `should return true when active clusters exist`() {
            // When
            val result = client.isAvailable()

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false when no active clusters")
        fun `should return false when no active clusters`() {
            // Given
            every { clusterRepository.findAllActive() } returns emptyList()
            val clientWithNoClusters = MockRestAirflowClient(clusterRepository)

            // When
            val result = clientWithNoClusters.isAvailable()

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("getTaskInstances")
    inner class GetTaskInstances {
        @Test
        @DisplayName("should return mock task instances")
        fun `should return mock task instances`() {
            // Given
            val dagId = "dag_data-platform__my_workflow"
            val runId = "manual__2025-01-15T12:00:00"
            client.triggerDAGRun(dagId, runId, emptyMap())

            // When
            val taskInstances = client.getTaskInstances(dagId, runId)

            // Then
            assertThat(taskInstances).isNotEmpty()
            assertThat(taskInstances.first().dagId).isEqualTo(dagId)
            assertThat(taskInstances.first().dagRunId).isEqualTo(runId)
        }
    }

    @Nested
    @DisplayName("Cluster Resolution")
    inner class ClusterResolution {
        @Test
        @DisplayName("should throw exception when no active clusters available")
        fun `should throw exception when no active clusters available`() {
            // Given
            every { clusterRepository.findAllActive() } returns emptyList()
            every { clusterRepository.findByTeam(any()) } returns null
            val clientWithNoClusters = MockRestAirflowClient(clusterRepository)

            // When & Then
            assertThrows<AirflowConnectionException> {
                clientWithNoClusters.triggerDAGRun("dag_unknown__workflow", "run1", emptyMap())
            }
        }
    }
}
