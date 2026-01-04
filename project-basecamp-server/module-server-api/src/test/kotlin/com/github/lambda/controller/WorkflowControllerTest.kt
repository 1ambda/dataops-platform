package com.github.lambda.controller

import com.github.lambda.common.exception.WorkflowAlreadyExistsException
import com.github.lambda.common.exception.WorkflowNotFoundException
import com.github.lambda.common.exception.WorkflowRunNotFoundException
import com.github.lambda.config.SecurityConfig
import com.github.lambda.domain.entity.workflow.WorkflowEntity
import com.github.lambda.domain.entity.workflow.WorkflowRunEntity
import com.github.lambda.domain.model.workflow.ScheduleInfo
import com.github.lambda.domain.model.workflow.WorkflowRunStatus
import com.github.lambda.domain.model.workflow.WorkflowRunType
import com.github.lambda.domain.model.workflow.WorkflowSourceType
import com.github.lambda.domain.model.workflow.WorkflowStatus
import com.github.lambda.domain.service.WorkflowService
import com.github.lambda.dto.workflow.BackfillRequest
import com.github.lambda.dto.workflow.BackfillResponseDto
import com.github.lambda.dto.workflow.PauseWorkflowRequest
import com.github.lambda.dto.workflow.RegisterWorkflowRequest
import com.github.lambda.dto.workflow.StopRunRequest
import com.github.lambda.dto.workflow.TriggerRunRequest
import com.github.lambda.dto.workflow.WorkflowDetailDto
import com.github.lambda.dto.workflow.WorkflowRunDetailDto
import com.github.lambda.dto.workflow.WorkflowRunSummaryDto
import com.github.lambda.dto.workflow.WorkflowSummaryDto
import com.github.lambda.exception.GlobalExceptionHandler
import com.github.lambda.mapper.WorkflowMapper
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * WorkflowController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only (faster than full integration test)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler for proper security and exception handling
 */
@WebMvcTest(WorkflowController::class)
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
    WorkflowControllerTest.ValidationConfig::class,
)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class WorkflowControllerTest {
    /**
     * Test configuration to enable method-level validation for @Min, @Max, @Size annotations
     * on controller method parameters. Required for @WebMvcTest since it doesn't auto-configure this.
     */
    @TestConfiguration
    class ValidationConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val jsonMapper: JsonMapper = JsonMapper.builder().build()

    @MockkBean(relaxed = true)
    private lateinit var workflowService: WorkflowService

    @MockkBean(relaxed = true)
    private lateinit var workflowMapper: WorkflowMapper

    // Test data
    private lateinit var testWorkflowEntity: WorkflowEntity
    private lateinit var testWorkflowRunEntity: WorkflowRunEntity
    private lateinit var testWorkflowSummaryDto: WorkflowSummaryDto
    private lateinit var testWorkflowDetailDto: WorkflowDetailDto
    private lateinit var testWorkflowRunSummaryDto: WorkflowRunSummaryDto
    private lateinit var testWorkflowRunDetailDto: WorkflowRunDetailDto

    private val testDatasetName = "my-project.analytics.users"
    private val testRunId = "my-project.analytics.users_manual_20260101_120000"
    private val testOwner = "data-team@example.com"
    private val testTeam = "@data-eng"
    private val testDescription = "User analytics workflow"
    private val testTimestamp = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    @BeforeEach
    fun setUp() {
        // Setup WorkflowEntity
        testWorkflowEntity =
            WorkflowEntity(
                datasetName = testDatasetName,
                sourceType = WorkflowSourceType.MANUAL,
                status = WorkflowStatus.ACTIVE,
                owner = testOwner,
                team = testTeam,
                description = testDescription,
                s3Path = "s3://bucket/workflows/$testDatasetName.yaml",
                airflowDagId = "my_project_analytics_users",
                schedule = ScheduleInfo(cron = "0 0 * * *", timezone = "UTC"),
            )

        // Setup WorkflowRunEntity
        testWorkflowRunEntity =
            WorkflowRunEntity(
                runId = testRunId,
                datasetName = testDatasetName,
                status = WorkflowRunStatus.RUNNING,
                triggeredBy = testOwner,
                runType = WorkflowRunType.MANUAL,
                startedAt = testTimestamp,
            )

        // Setup response DTOs
        testWorkflowSummaryDto =
            WorkflowSummaryDto(
                datasetName = testDatasetName,
                sourceType = "MANUAL",
                status = "ACTIVE",
                owner = testOwner,
                team = testTeam,
                description = testDescription,
                airflowDagId = "my_project_analytics_users",
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
            )

        testWorkflowDetailDto =
            WorkflowDetailDto(
                datasetName = testDatasetName,
                sourceType = "MANUAL",
                status = "ACTIVE",
                owner = testOwner,
                team = testTeam,
                description = testDescription,
                s3Path = "s3://bucket/workflows/$testDatasetName.yaml",
                airflowDagId = "my_project_analytics_users",
                schedule = "0 0 * * *",
                recentRuns = emptyList(),
                createdAt = testTimestamp,
                updatedAt = testTimestamp,
            )

        testWorkflowRunSummaryDto =
            WorkflowRunSummaryDto(
                runId = testRunId,
                datasetName = testDatasetName,
                status = "RUNNING",
                triggerMode = "MANUAL",
                executionDate = testTimestamp,
                startedAt = testTimestamp,
                completedAt = null,
                durationSeconds = null,
                triggeredBy = testOwner,
            )

        testWorkflowRunDetailDto =
            WorkflowRunDetailDto(
                runId = testRunId,
                datasetName = testDatasetName,
                status = "RUNNING",
                triggerMode = "MANUAL",
                executionDate = testTimestamp,
                parameters = null,
                startedAt = testTimestamp,
                completedAt = null,
                durationSeconds = null,
                triggeredBy = testOwner,
                airflowDagRunId = testRunId,
                airflowLogUrl = null,
                errorMessage = null,
            )
    }

    @Nested
    @DisplayName("GET /api/v1/workflows")
    inner class ListWorkflows {
        @Test
        @DisplayName("should return empty list when no workflows exist")
        fun `should return empty list when no workflows exist`() {
            // Given
            every { workflowService.getWorkflows(any(), any(), any(), any(), any()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/workflows"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { workflowService.getWorkflows(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("should return workflows list")
        fun `should return workflows list`() {
            // Given
            every { workflowService.getWorkflows(any(), any(), any(), any(), any()) } returns listOf(testWorkflowEntity)
            every { workflowMapper.toSummaryDto(testWorkflowEntity) } returns testWorkflowSummaryDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/workflows"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dataset_name").value(testDatasetName))
                .andExpect(jsonPath("$[0].source_type").value("MANUAL"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].owner").value(testOwner))

            verify(exactly = 1) { workflowService.getWorkflows(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("should filter workflows by status")
        fun `should filter workflows by status`() {
            // Given
            val status = "ACTIVE"
            every {
                workflowService.getWorkflows(
                    status = status,
                    sourceType = any(),
                    owner = any(),
                    limit = any(),
                    offset = any(),
                )
            } returns listOf(testWorkflowEntity)
            every { workflowMapper.toSummaryDto(testWorkflowEntity) } returns testWorkflowSummaryDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/workflows")
                        .param("status", status),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                workflowService.getWorkflows(
                    status = status,
                    sourceType = any(),
                    owner = any(),
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter workflows by sourceType")
        fun `should filter workflows by sourceType`() {
            // Given
            val sourceType = "MANUAL"
            every {
                workflowService.getWorkflows(
                    status = any(),
                    sourceType = sourceType,
                    owner = any(),
                    limit = any(),
                    offset = any(),
                )
            } returns listOf(testWorkflowEntity)
            every { workflowMapper.toSummaryDto(testWorkflowEntity) } returns testWorkflowSummaryDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/workflows")
                        .param("sourceType", sourceType),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                workflowService.getWorkflows(
                    status = any(),
                    sourceType = sourceType,
                    owner = any(),
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter workflows by owner")
        fun `should filter workflows by owner`() {
            // Given
            every {
                workflowService.getWorkflows(
                    status = any(),
                    sourceType = any(),
                    owner = testOwner,
                    limit = any(),
                    offset = any(),
                )
            } returns listOf(testWorkflowEntity)
            every { workflowMapper.toSummaryDto(testWorkflowEntity) } returns testWorkflowSummaryDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/workflows")
                        .param("owner", testOwner),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                workflowService.getWorkflows(
                    status = any(),
                    sourceType = any(),
                    owner = testOwner,
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should apply limit and offset")
        fun `should apply limit and offset`() {
            // Given
            val limit = 10
            val offset = 5
            every {
                workflowService.getWorkflows(
                    status = any(),
                    sourceType = any(),
                    owner = any(),
                    limit = limit,
                    offset = offset,
                )
            } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/workflows")
                        .param("limit", limit.toString())
                        .param("offset", offset.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                workflowService.getWorkflows(
                    status = any(),
                    sourceType = any(),
                    owner = any(),
                    limit = limit,
                    offset = offset,
                )
            }
        }

        @Test
        @DisplayName("should reject request when limit exceeds maximum")
        fun `should reject request when limit exceeds maximum`() {
            // When limit exceeds 500, validation should reject the request
            mockMvc
                .perform(
                    get("/api/v1/workflows")
                        .param("limit", "501"),
                ).andExpect(status().is4xxClientError)
        }

        @Test
        @DisplayName("should reject request when offset is negative")
        fun `should reject request when offset is negative`() {
            mockMvc
                .perform(
                    get("/api/v1/workflows")
                        .param("offset", "-1"),
                ).andExpect(status().is4xxClientError)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workflows/runs/{run_id}")
    inner class GetWorkflowRunStatus {
        @Test
        @DisplayName("should return run detail for existing run")
        fun `should return run detail for existing run`() {
            // Given
            every { workflowService.getWorkflowRunWithSync(testRunId) } returns testWorkflowRunEntity
            every { workflowMapper.toRunDetailDto(testWorkflowRunEntity) } returns testWorkflowRunDetailDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/workflows/runs/$testRunId"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.run_id").value(testRunId))
                .andExpect(jsonPath("$.dataset_name").value(testDatasetName))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.trigger_mode").value("MANUAL"))

            verify(exactly = 1) { workflowService.getWorkflowRunWithSync(testRunId) }
        }

        @Test
        @DisplayName("should return 404 when run not found")
        fun `should return 404 when run not found`() {
            // Given
            val runId = "nonexistent_run_id"
            every { workflowService.getWorkflowRunWithSync(runId) } throws WorkflowRunNotFoundException(runId)

            // When & Then
            mockMvc
                .perform(get("/api/v1/workflows/runs/$runId"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { workflowService.getWorkflowRunWithSync(runId) }
        }

        @Test
        @DisplayName("should return run detail with completed status")
        fun `should return run detail with completed status`() {
            // Given
            val completedRun =
                testWorkflowRunEntity.copy().apply {
                    status = WorkflowRunStatus.SUCCESS
                    endedAt = testTimestamp.plusHours(1)
                }
            val completedDto =
                testWorkflowRunDetailDto.copy(
                    status = "SUCCESS",
                    completedAt = testTimestamp.plusHours(1),
                    durationSeconds = 3600L,
                )
            every { workflowService.getWorkflowRunWithSync(testRunId) } returns completedRun
            every { workflowMapper.toRunDetailDto(completedRun) } returns completedDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/workflows/runs/$testRunId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.duration_seconds").value(3600))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workflows/history")
    inner class GetWorkflowHistory {
        @Test
        @DisplayName("should return empty list when no history exists")
        fun `should return empty list when no history exists`() {
            // Given
            every { workflowService.getWorkflowHistory(any(), any(), any(), any()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/workflows/history"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { workflowService.getWorkflowHistory(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("should return workflow history")
        fun `should return workflow history`() {
            // Given
            every {
                workflowService.getWorkflowHistory(any(), any(), any(), any())
            } returns listOf(testWorkflowRunEntity)
            every { workflowMapper.toRunSummaryDto(testWorkflowRunEntity) } returns testWorkflowRunSummaryDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/workflows/history"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].run_id").value(testRunId))
                .andExpect(jsonPath("$[0].dataset_name").value(testDatasetName))
        }

        @Test
        @DisplayName("should filter history by dataset name")
        fun `should filter history by dataset name`() {
            // Given
            every {
                workflowService.getWorkflowHistory(
                    datasetName = testDatasetName,
                    startDate = any(),
                    endDate = any(),
                    limit = any(),
                )
            } returns listOf(testWorkflowRunEntity)
            every { workflowMapper.toRunSummaryDto(testWorkflowRunEntity) } returns testWorkflowRunSummaryDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/workflows/history")
                        .param("datasetName", testDatasetName),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                workflowService.getWorkflowHistory(
                    datasetName = testDatasetName,
                    startDate = any(),
                    endDate = any(),
                    limit = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter history by date range")
        fun `should filter history by date range`() {
            // Given
            val startDate = "2026-01-01"
            val endDate = "2026-01-31"
            every {
                workflowService.getWorkflowHistory(
                    datasetName = any(),
                    startDate = startDate,
                    endDate = endDate,
                    limit = any(),
                )
            } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/workflows/history")
                        .param("startDate", startDate)
                        .param("endDate", endDate),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                workflowService.getWorkflowHistory(
                    datasetName = any(),
                    startDate = startDate,
                    endDate = endDate,
                    limit = any(),
                )
            }
        }

        @Test
        @DisplayName("should apply limit parameter")
        fun `should apply limit parameter`() {
            // Given
            val limit = 10
            every {
                workflowService.getWorkflowHistory(
                    datasetName = any(),
                    startDate = any(),
                    endDate = any(),
                    limit = limit,
                )
            } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/workflows/history")
                        .param("limit", limit.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                workflowService.getWorkflowHistory(
                    datasetName = any(),
                    startDate = any(),
                    endDate = any(),
                    limit = limit,
                )
            }
        }

        @Test
        @DisplayName("should reject request when limit exceeds maximum")
        fun `should reject request when limit exceeds maximum`() {
            mockMvc
                .perform(
                    get("/api/v1/workflows/history")
                        .param("limit", "101"), // Max is 100
                ).andExpect(status().is4xxClientError)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workflows/register")
    inner class RegisterWorkflow {
        @Test
        @DisplayName("should register workflow successfully")
        fun `should register workflow successfully`() {
            // Given
            val request =
                RegisterWorkflowRequest(
                    datasetName = testDatasetName,
                    sourceType = "MANUAL",
                    owner = testOwner,
                    team = testTeam,
                    description = testDescription,
                    scheduleCron = "0 0 * * *",
                    scheduleTimezone = "UTC",
                    yamlContent = "version: 1\nname: test",
                )

            every {
                workflowService.registerWorkflow(
                    datasetName = testDatasetName,
                    sourceType = WorkflowSourceType.MANUAL,
                    schedule = any(),
                    owner = testOwner,
                    team = testTeam,
                    description = testDescription,
                    yamlContent = "version: 1\nname: test",
                )
            } returns testWorkflowEntity
            every { workflowMapper.toDetailDto(testWorkflowEntity) } returns testWorkflowDetailDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dataset_name").value(testDatasetName))
                .andExpect(jsonPath("$.source_type").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))

            verify(exactly = 1) {
                workflowService.registerWorkflow(
                    datasetName = testDatasetName,
                    sourceType = WorkflowSourceType.MANUAL,
                    schedule = any(),
                    owner = testOwner,
                    team = testTeam,
                    description = testDescription,
                    yamlContent = "version: 1\nname: test",
                )
            }
        }

        @Test
        @DisplayName("should return 409 when workflow already exists")
        fun `should return 409 when workflow already exists`() {
            // Given
            val request =
                RegisterWorkflowRequest(
                    datasetName = testDatasetName,
                    sourceType = "MANUAL",
                    owner = testOwner,
                    team = testTeam,
                    description = testDescription,
                    scheduleCron = "0 0 * * *",
                    yamlContent = "version: 1\nname: test",
                )

            every {
                workflowService.registerWorkflow(
                    datasetName = any(),
                    sourceType = any(),
                    schedule = any(),
                    owner = any(),
                    team = any(),
                    description = any(),
                    yamlContent = any(),
                )
            } throws WorkflowAlreadyExistsException(testDatasetName)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }

        @Test
        @DisplayName("should return 400 when request is invalid")
        fun `should return 400 when request is invalid`() {
            // Given - empty dataset name
            val request =
                mapOf(
                    "dataset_name" to "",
                    "source_type" to "MANUAL",
                    "owner" to testOwner,
                    "yaml_content" to "version: 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workflows/{dataset_name}/run")
    inner class TriggerWorkflowRun {
        @Test
        @DisplayName("should trigger workflow run successfully")
        fun `should trigger workflow run successfully`() {
            // Given
            val request =
                TriggerRunRequest(
                    parameters = mapOf("date" to "2026-01-01"),
                    dryRun = false,
                )

            every {
                workflowService.triggerWorkflowRun(
                    datasetName = testDatasetName,
                    params = mapOf("date" to "2026-01-01"),
                    dryRun = false,
                )
            } returns testWorkflowRunEntity
            every { workflowMapper.toRunDetailDto(testWorkflowRunEntity) } returns testWorkflowRunDetailDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isAccepted)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.run_id").value(testRunId))
                .andExpect(jsonPath("$.dataset_name").value(testDatasetName))
                .andExpect(jsonPath("$.status").value("RUNNING"))

            verify(exactly = 1) {
                workflowService.triggerWorkflowRun(
                    datasetName = testDatasetName,
                    params = mapOf("date" to "2026-01-01"),
                    dryRun = false,
                )
            }
        }

        @Test
        @DisplayName("should trigger dry run successfully")
        fun `should trigger dry run successfully`() {
            // Given
            val request = TriggerRunRequest(dryRun = true)

            every {
                workflowService.triggerWorkflowRun(
                    datasetName = testDatasetName,
                    params = emptyMap(),
                    dryRun = true,
                )
            } returns testWorkflowRunEntity
            every { workflowMapper.toRunDetailDto(testWorkflowRunEntity) } returns testWorkflowRunDetailDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isAccepted)

            verify(exactly = 1) {
                workflowService.triggerWorkflowRun(
                    datasetName = testDatasetName,
                    params = emptyMap(),
                    dryRun = true,
                )
            }
        }

        @Test
        @DisplayName("should return 404 when workflow not found")
        fun `should return 404 when workflow not found`() {
            // Given
            val request = TriggerRunRequest()
            every {
                workflowService.triggerWorkflowRun(any(), any(), any())
            } throws WorkflowNotFoundException(testDatasetName)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workflows/{dataset_name}/backfill")
    inner class TriggerBackfill {
        @Test
        @DisplayName("should trigger backfill successfully")
        fun `should trigger backfill successfully`() {
            // Given
            val request =
                BackfillRequest(
                    startDate = "2026-01-01",
                    endDate = "2026-01-05",
                    parameters = emptyMap(),
                )

            val backfillRuns =
                listOf(
                    testWorkflowRunEntity.copy().apply { runId = "run_2026-01-01" },
                    testWorkflowRunEntity.copy().apply { runId = "run_2026-01-02" },
                )

            val backfillResponse =
                BackfillResponseDto(
                    backfillId = "backfill_123",
                    datasetName = testDatasetName,
                    startDate = "2026-01-01",
                    endDate = "2026-01-05",
                    totalRuns = 2,
                    runIds = listOf("run_2026-01-01", "run_2026-01-02"),
                    status = "PENDING",
                    createdAt = testTimestamp,
                )

            every {
                workflowService.triggerBackfill(any(), any(), any(), any())
            } returns backfillRuns
            every {
                workflowMapper.toBackfillResponseDto(
                    backfillId = any(),
                    datasetName = any(),
                    startDate = any(),
                    endDate = any(),
                    runIds = any(),
                    status = any(),
                    createdAt = any(),
                )
            } returns backfillResponse

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isAccepted)
                .andExpect(jsonPath("$.dataset_name").value(testDatasetName))
                .andExpect(jsonPath("$.start_date").value("2026-01-01"))
                .andExpect(jsonPath("$.end_date").value("2026-01-05"))
                .andExpect(jsonPath("$.total_runs").value(2))
        }

        @Test
        @DisplayName("should return 404 when workflow not found")
        fun `should return 404 when workflow not found`() {
            // Given
            val request =
                BackfillRequest(
                    startDate = "2026-01-01",
                    endDate = "2026-01-05",
                )
            every {
                workflowService.triggerBackfill(any(), any(), any(), any())
            } throws WorkflowNotFoundException(testDatasetName)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 400 when dates are missing")
        fun `should return 400 when dates are missing`() {
            // Given - empty dates
            val request =
                mapOf(
                    "start_date" to "",
                    "end_date" to "",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workflows/runs/{run_id}/stop")
    inner class StopWorkflowRun {
        @Test
        @DisplayName("should stop workflow run successfully")
        fun `should stop workflow run successfully`() {
            // Given
            val request = StopRunRequest(reason = "User requested stop")
            val stoppedRun =
                testWorkflowRunEntity.copy().apply {
                    status = WorkflowRunStatus.STOPPING
                }
            val stoppedDto =
                testWorkflowRunDetailDto.copy(
                    status = "STOPPING",
                )

            every { workflowService.stopWorkflowRun(testRunId, "User requested stop") } returns stoppedRun
            every { workflowMapper.toRunDetailDto(stoppedRun) } returns stoppedDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/runs/$testRunId/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.run_id").value(testRunId))
                .andExpect(jsonPath("$.status").value("STOPPING"))

            verify(exactly = 1) { workflowService.stopWorkflowRun(testRunId, "User requested stop") }
        }

        @Test
        @DisplayName("should return 404 when run not found")
        fun `should return 404 when run not found`() {
            // Given
            val request = StopRunRequest(reason = null)
            every { workflowService.stopWorkflowRun(any(), any()) } throws WorkflowRunNotFoundException(testRunId)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/runs/$testRunId/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workflows/{dataset_name}/pause")
    inner class PauseWorkflow {
        @Test
        @DisplayName("should pause workflow successfully")
        fun `should pause workflow successfully`() {
            // Given
            val request = PauseWorkflowRequest(reason = "Maintenance")
            val pausedWorkflow =
                testWorkflowEntity.copy().apply {
                    status = WorkflowStatus.PAUSED
                }
            val pausedDto = testWorkflowDetailDto.copy(status = "PAUSED")

            every { workflowService.pauseWorkflow(testDatasetName, "Maintenance") } returns pausedWorkflow
            every { workflowMapper.toDetailDto(pausedWorkflow) } returns pausedDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.dataset_name").value(testDatasetName))
                .andExpect(jsonPath("$.status").value("PAUSED"))

            verify(exactly = 1) { workflowService.pauseWorkflow(testDatasetName, "Maintenance") }
        }

        @Test
        @DisplayName("should return 404 when workflow not found")
        fun `should return 404 when workflow not found`() {
            // Given
            val request = PauseWorkflowRequest(reason = null)
            every { workflowService.pauseWorkflow(any(), any()) } throws WorkflowNotFoundException(testDatasetName)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/workflows/$testDatasetName/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workflows/{dataset_name}/unpause")
    inner class UnpauseWorkflow {
        @Test
        @DisplayName("should unpause workflow successfully")
        fun `should unpause workflow successfully`() {
            // Given
            every { workflowService.unpauseWorkflow(testDatasetName) } returns testWorkflowEntity
            every { workflowMapper.toDetailDto(testWorkflowEntity) } returns testWorkflowDetailDto

            // When & Then
            mockMvc
                .perform(post("/api/v1/workflows/$testDatasetName/unpause"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.dataset_name").value(testDatasetName))
                .andExpect(jsonPath("$.status").value("ACTIVE"))

            verify(exactly = 1) { workflowService.unpauseWorkflow(testDatasetName) }
        }

        @Test
        @DisplayName("should return 404 when workflow not found")
        fun `should return 404 when workflow not found`() {
            // Given
            every { workflowService.unpauseWorkflow(any()) } throws WorkflowNotFoundException(testDatasetName)

            // When & Then
            mockMvc
                .perform(post("/api/v1/workflows/$testDatasetName/unpause"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/workflows/{dataset_name}")
    inner class UnregisterWorkflow {
        @Test
        @DisplayName("should unregister workflow successfully")
        fun `should unregister workflow successfully`() {
            // Given
            every { workflowService.unregisterWorkflow(testDatasetName, false) } returns testWorkflowEntity

            // When & Then
            mockMvc
                .perform(delete("/api/v1/workflows/$testDatasetName"))
                .andExpect(status().isNoContent)

            verify(exactly = 1) { workflowService.unregisterWorkflow(testDatasetName, false) }
        }

        @Test
        @DisplayName("should force unregister workflow")
        fun `should force unregister workflow`() {
            // Given
            every { workflowService.unregisterWorkflow(testDatasetName, true) } returns testWorkflowEntity

            // When & Then
            mockMvc
                .perform(
                    delete("/api/v1/workflows/$testDatasetName")
                        .param("force", "true"),
                ).andExpect(status().isNoContent)

            verify(exactly = 1) { workflowService.unregisterWorkflow(testDatasetName, true) }
        }

        @Test
        @DisplayName("should return 404 when workflow not found")
        fun `should return 404 when workflow not found`() {
            // Given
            every { workflowService.unregisterWorkflow(any(), any()) } throws WorkflowNotFoundException(testDatasetName)

            // When & Then
            mockMvc
                .perform(delete("/api/v1/workflows/$testDatasetName"))
                .andExpect(status().isNotFound)
        }
    }

    // Helper extension function
    private fun WorkflowRunEntity.copy(): WorkflowRunEntity =
        WorkflowRunEntity(
            runId = this.runId,
            datasetName = this.datasetName,
            status = this.status,
            triggeredBy = this.triggeredBy,
            runType = this.runType,
            startedAt = this.startedAt,
            endedAt = this.endedAt,
            params = this.params,
            logsUrl = this.logsUrl,
            stopReason = this.stopReason,
            stoppedBy = this.stoppedBy,
            stoppedAt = this.stoppedAt,
        )

    private fun WorkflowEntity.copy(): WorkflowEntity =
        WorkflowEntity(
            datasetName = this.datasetName,
            sourceType = this.sourceType,
            status = this.status,
            owner = this.owner,
            team = this.team,
            description = this.description,
            s3Path = this.s3Path,
            airflowDagId = this.airflowDagId,
            schedule = this.schedule,
        )
}
