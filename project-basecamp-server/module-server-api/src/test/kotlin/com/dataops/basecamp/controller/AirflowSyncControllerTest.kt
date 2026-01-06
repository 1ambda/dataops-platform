package com.dataops.basecamp.controller

import com.dataops.basecamp.domain.projection.workflow.ClusterSyncProjection
import com.dataops.basecamp.domain.projection.workflow.RunSyncProjection
import com.dataops.basecamp.domain.projection.workflow.SpecSyncProjection
import com.dataops.basecamp.domain.service.AirflowService
import com.dataops.basecamp.domain.service.WorkflowService
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
    private lateinit var specSyncService: WorkflowService

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
    @DisplayName("POST /api/v1/airflow/sync/manual/specs - Trigger spec sync")
    inner class TriggerSpecSync {
        @Test
        @WithMockUser
        fun `should return 200 when spec sync succeeds`() {
            // given
            every { specSyncService.syncFromStorage() } returns testSpecSyncResult
            every { mapper.toSpecSyncResultDto(testSpecSyncResult) } returns testSpecSyncResultDto

            // when & then
            mockMvc
                .perform(
                    post("$apiBasePath/specs")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalProcessed").value(5))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.failed").value(1))

            verify(exactly = 1) { specSyncService.syncFromStorage() }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/airflow/sync/manual/runs - Trigger run sync")
    inner class TriggerRunSync {
        @Test
        @WithMockUser
        fun `should return 200 when run sync succeeds`() {
            // given
            every { runSyncService.syncAllClusters(24, 100) } returns testRunSyncResult
            every { mapper.toRunSyncResultDto(testRunSyncResult) } returns testRunSyncResultDto

            // when & then
            mockMvc
                .perform(
                    post("$apiBasePath/runs")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalClusters").value(1))
                .andExpect(jsonPath("$.clusterResults[0].clusterId").value(1))
                .andExpect(jsonPath("$.clusterResults[0].success").value(true))

            verify(exactly = 1) { runSyncService.syncAllClusters(24, 100) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/airflow/sync/manual/runs/cluster/:id - Trigger cluster run sync")
    inner class TriggerClusterRunSync {
        @Test
        @WithMockUser
        fun `should return 200 when cluster run sync succeeds`() {
            // given
            val clusterId = 1L
            every { runSyncService.syncCluster(clusterId, 24, 100) } returns testClusterSyncResult
            every { mapper.toClusterSyncResultDto(testClusterSyncResult) } returns testClusterSyncResultDto

            // when & then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/cluster/$clusterId")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.clusterId").value(1))
                .andExpect(jsonPath("$.clusterName").value("data-platform"))
                .andExpect(jsonPath("$.success").value(true))

            verify(exactly = 1) { runSyncService.syncCluster(clusterId, 24, 100) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/airflow/sync/manual/runs/stale - Trigger stale runs sync")
    inner class TriggerStaleRunsSync {
        @Test
        @WithMockUser
        fun `should return 200 when stale runs sync succeeds`() {
            // given
            every { runSyncService.syncStaleRuns(1) } returns testRunSyncResult
            every { mapper.toRunSyncResultDto(testRunSyncResult) } returns testRunSyncResultDto

            // when & then
            mockMvc
                .perform(
                    post("$apiBasePath/runs/stale")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalClusters").value(1))

            verify(exactly = 1) { runSyncService.syncStaleRuns(1) }
        }
    }
}
