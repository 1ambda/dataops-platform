package com.dataops.basecamp.controller

import com.dataops.basecamp.domain.projection.workflow.ClusterSyncProjection
import com.dataops.basecamp.domain.projection.workflow.RunSyncProjection
import com.dataops.basecamp.domain.projection.workflow.SpecSyncProjection
import com.dataops.basecamp.domain.service.AirflowService
import com.dataops.basecamp.domain.service.WorkflowSpecSyncService
import com.dataops.basecamp.dto.airflow.ClusterSyncResultDto
import com.dataops.basecamp.dto.airflow.RunSyncResultDto
import com.dataops.basecamp.dto.airflow.SpecSyncResultDto
import com.dataops.basecamp.mapper.AirflowSyncMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import java.time.Instant

/**
 * AirflowSyncController REST API Tests
 *
 * Tests for manual Airflow sync trigger endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class AirflowSyncControllerTest {
    @TestConfiguration
    class ValidationConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean(relaxed = true)
    private lateinit var specSyncService: WorkflowSpecSyncService

    @MockkBean(relaxed = true)
    private lateinit var runSyncService: AirflowService

    @MockkBean(relaxed = true)
    private lateinit var mapper: AirflowSyncMapper

    private val apiBasePath = "/api/v1/airflow/sync/manual"
    private val fixedInstant = Instant.parse("2025-01-15T12:00:00Z")

    private lateinit var testSpecSyncResult: SpecSyncProjection
    private lateinit var testRunSyncResult: RunSyncProjection
    private lateinit var testClusterSyncResult: ClusterSyncProjection
    private lateinit var testSpecSyncResultDto: SpecSyncResultDto
    private lateinit var testRunSyncResultDto: RunSyncResultDto
    private lateinit var testClusterSyncResultDto: ClusterSyncResultDto

    @BeforeEach
    fun setUp() {
        testSpecSyncResult =
            SpecSyncProjection(
                totalProcessed = 5,
                created = 2,
                updated = 2,
                failed = 1,
                errors = emptyList(),
                syncedAt = fixedInstant,
            )

        testClusterSyncResult =
            ClusterSyncProjection.success(
                clusterId = 1L,
                clusterName = "data-platform",
                updatedCount = 10,
                createdCount = 0,
            )

        testRunSyncResult =
            RunSyncProjection(
                totalClusters = 1,
                clusterResults = listOf(testClusterSyncResult),
                syncedAt = fixedInstant,
            )

        testSpecSyncResultDto =
            SpecSyncResultDto(
                totalProcessed = 5,
                created = 2,
                updated = 2,
                failed = 1,
                errors = emptyList(),
                syncedAt = fixedInstant,
                success = true,
                summary = "Processed 5 specs: 2 created, 2 updated, 1 failed",
            )

        testClusterSyncResultDto =
            ClusterSyncResultDto(
                clusterId = 1L,
                clusterName = "data-platform",
                updatedCount = 10,
                createdCount = 0,
                totalProcessed = 10,
                error = null,
                success = true,
            )

        testRunSyncResultDto =
            RunSyncResultDto(
                totalClusters = 1,
                clusterResults = listOf(testClusterSyncResultDto),
                syncedAt = fixedInstant,
                totalUpdated = 10,
                totalCreated = 0,
                failedClusters = 0,
                success = true,
            )
    }

    @Nested
    @DisplayName("POST /specs - Trigger S3 Spec Sync")
    inner class TriggerSpecSync {
        @Test
        @DisplayName("should trigger spec sync and return result")
        @WithMockUser(roles = ["ADMIN"])
        fun `should trigger spec sync and return result`() {
            // Given
            every { specSyncService.syncFromStorage() } returns testSpecSyncResult
            every { mapper.toSpecSyncResultDto(testSpecSyncResult) } returns testSpecSyncResultDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/specs")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalProcessed").value(5))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.success").value(true))

            verify(exactly = 1) { specSyncService.syncFromStorage() }
        }

        @Test
        @DisplayName("should return 403 when user is not admin")
        @WithMockUser(roles = ["USER"])
        fun `should return 403 when user is not admin`() {
            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/specs")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isForbidden)
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        fun `should return 401 when not authenticated`() {
            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/specs")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("POST /runs - Trigger Run Sync for All Clusters")
    inner class TriggerRunSync {
        @Test
        @DisplayName("should trigger run sync for all clusters")
        @WithMockUser(roles = ["ADMIN"])
        fun `should trigger run sync for all clusters`() {
            // Given
            every { runSyncService.syncAllClusters(any(), any()) } returns testRunSyncResult
            every { mapper.toRunSyncResultDto(testRunSyncResult) } returns testRunSyncResultDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalClusters").value(1))
                .andExpect(jsonPath("$.totalUpdated").value(10))
                .andExpect(jsonPath("$.success").value(true))

            verify(exactly = 1) { runSyncService.syncAllClusters(24, 100) }
        }

        @Test
        @DisplayName("should use custom lookback hours and batch size")
        @WithMockUser(roles = ["ADMIN"])
        fun `should use custom lookback hours and batch size`() {
            // Given
            every { runSyncService.syncAllClusters(any(), any()) } returns testRunSyncResult
            every { mapper.toRunSyncResultDto(testRunSyncResult) } returns testRunSyncResultDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs")
                        .param("lookbackHours", "48")
                        .param("batchSize", "200")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

            verify(exactly = 1) { runSyncService.syncAllClusters(48, 200) }
        }

        @Test
        @DisplayName("should return 403 when user is not admin")
        @WithMockUser(roles = ["USER"])
        fun `should return 403 when user is not admin`() {
            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    @DisplayName("POST /runs/cluster/{clusterId} - Trigger Run Sync for Specific Cluster")
    inner class TriggerClusterRunSync {
        @Test
        @DisplayName("should trigger run sync for specific cluster")
        @WithMockUser(roles = ["ADMIN"])
        fun `should trigger run sync for specific cluster`() {
            // Given
            every { runSyncService.syncCluster(1L, any(), any()) } returns testClusterSyncResult
            every { mapper.toClusterSyncResultDto(testClusterSyncResult) } returns testClusterSyncResultDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/cluster/1")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.clusterId").value(1))
                .andExpect(jsonPath("$.clusterName").value("data-platform"))
                .andExpect(jsonPath("$.updatedCount").value(10))
                .andExpect(jsonPath("$.success").value(true))

            verify(exactly = 1) { runSyncService.syncCluster(1L, 24, 100) }
        }

        @Test
        @DisplayName("should use custom parameters")
        @WithMockUser(roles = ["ADMIN"])
        fun `should use custom parameters`() {
            // Given
            every { runSyncService.syncCluster(2L, any(), any()) } returns testClusterSyncResult
            every { mapper.toClusterSyncResultDto(testClusterSyncResult) } returns testClusterSyncResultDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/cluster/2")
                        .param("lookbackHours", "12")
                        .param("batchSize", "50")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

            verify(exactly = 1) { runSyncService.syncCluster(2L, 12, 50) }
        }

        @Test
        @DisplayName("should return error when cluster not found")
        @WithMockUser(roles = ["ADMIN"])
        fun `should return error when cluster not found`() {
            // Given
            val failedResult =
                ClusterSyncProjection.failure(
                    clusterId = 999L,
                    clusterName = "unknown",
                    error = "Cluster not found: 999",
                )
            val failedDto =
                ClusterSyncResultDto(
                    clusterId = 999L,
                    clusterName = "unknown",
                    updatedCount = 0,
                    createdCount = 0,
                    totalProcessed = 0,
                    error = "Cluster not found: 999",
                    success = false,
                )

            every { runSyncService.syncCluster(999L, any(), any()) } returns failedResult
            every { mapper.toClusterSyncResultDto(failedResult) } returns failedDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/cluster/999")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Cluster not found: 999"))
        }
    }

    @Nested
    @DisplayName("POST /runs/stale - Trigger Stale Runs Sync")
    inner class TriggerStaleRunsSync {
        @Test
        @DisplayName("should trigger stale runs sync")
        @WithMockUser(roles = ["ADMIN"])
        fun `should trigger stale runs sync`() {
            // Given
            every { runSyncService.syncStaleRuns(any()) } returns testRunSyncResult
            every { mapper.toRunSyncResultDto(testRunSyncResult) } returns testRunSyncResultDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/stale")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))

            verify(exactly = 1) { runSyncService.syncStaleRuns(1) }
        }

        @Test
        @DisplayName("should use custom stale threshold")
        @WithMockUser(roles = ["ADMIN"])
        fun `should use custom stale threshold`() {
            // Given
            every { runSyncService.syncStaleRuns(any()) } returns testRunSyncResult
            every { mapper.toRunSyncResultDto(testRunSyncResult) } returns testRunSyncResultDto

            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/stale")
                        .param("staleThresholdHours", "2")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

            verify(exactly = 1) { runSyncService.syncStaleRuns(2) }
        }

        @Test
        @DisplayName("should return 403 when user is not admin")
        @WithMockUser(roles = ["USER"])
        fun `should return 403 when user is not admin`() {
            // When & Then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/stale")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isForbidden)
        }
    }
}
