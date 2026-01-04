package com.github.lambda.controller

import com.github.lambda.common.enums.ResourceType
import com.github.lambda.common.enums.Severity
import com.github.lambda.common.enums.TestType
import com.github.lambda.common.enums.WorkflowRunStatus
import com.github.lambda.common.exception.QualitySpecNotFoundException
import com.github.lambda.config.SecurityConfig
import com.github.lambda.domain.entity.quality.QualityRunEntity
import com.github.lambda.domain.entity.quality.QualitySpecEntity
import com.github.lambda.domain.entity.quality.QualityTestEntity
import com.github.lambda.domain.service.QualityService
import com.github.lambda.dto.quality.ExecuteQualityTestRequest
import com.github.lambda.dto.quality.QualityRunResultDto
import com.github.lambda.dto.quality.QualityRunSummaryDto
import com.github.lambda.dto.quality.QualitySpecDetailDto
import com.github.lambda.dto.quality.QualitySpecSummaryDto
import com.github.lambda.dto.quality.QualityTestDto
import com.github.lambda.dto.quality.TestResultSummaryDto
import com.github.lambda.exception.GlobalExceptionHandler
import com.github.lambda.mapper.QualityMapper
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDateTime

/**
 * QualityController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only (faster than full integration test)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler for proper security and exception handling
 */
@WebMvcTest(QualityController::class)
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
    QualityControllerTest.TestConfig::class,
)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class QualityControllerTest {
    /**
     * Test configuration to enable method-level validation.
     * Required for @WebMvcTest since it doesn't auto-configure this.
     */
    @TestConfiguration
    class TestConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val jsonMapper: JsonMapper = JsonMapper.builder().build()

    @MockkBean(relaxed = true)
    private lateinit var qualityService: QualityService

    @MockkBean(relaxed = true)
    private lateinit var qualityMapper: QualityMapper

    // Domain entities
    private lateinit var testQualitySpec: QualitySpecEntity
    private lateinit var testQualityTest: QualityTestEntity
    private lateinit var testQualityRun: QualityRunEntity

    // Response DTOs
    private lateinit var testQualitySpecSummary: QualitySpecSummaryDto
    private lateinit var testQualitySpecDetail: QualitySpecDetailDto
    private lateinit var testQualityRunResult: QualityRunResultDto
    private lateinit var testQualityRunSummary: QualityRunSummaryDto

    @BeforeEach
    fun setUp() {
        // Setup domain entities
        testQualitySpec =
            QualitySpecEntity(
                name = "dataset_users_quality",
                resourceName = "analytics.users",
                resourceType = ResourceType.DATASET,
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "Quality tests for users dataset",
                scheduleCron = "0 0 * * *",
                scheduleTimezone = "UTC",
                enabled = true,
            ).apply {
                updateTags(setOf("tier::critical", "domain::analytics"))
            }

        testQualityTest =
            QualityTestEntity(
                name = "user_id_not_null",
                testType = TestType.NOT_NULL,
                description = "Ensure user_id is never null",
                targetColumns = mutableListOf("user_id"),
                severity = Severity.ERROR,
                enabled = true,
            )

        testQualityRun =
            QualityRunEntity(
                runId = "run-20260101-001",
                qualitySpecId = 1L,
                specName = "dataset_users_quality",
                targetResource = "analytics.users",
                targetResourceType = ResourceType.DATASET,
                status = WorkflowRunStatus.SUCCESS,
                triggeredBy = "system",
                startedAt = LocalDateTime.parse("2026-01-01T08:00:00"),
                endedAt = LocalDateTime.parse("2026-01-01T08:00:02"),
                passedTests = 3,
                failedTests = 0,
                totalTests = 3,
            )

        // Setup response DTOs
        testQualitySpecSummary =
            QualitySpecSummaryDto(
                name = "dataset_users_quality",
                resourceName = "analytics.users",
                resourceType = "DATASET",
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "Quality tests for users dataset",
                tags = listOf("domain::analytics", "tier::critical"),
                scheduleCron = "0 0 * * *",
                scheduleTimezone = "UTC",
                enabled = true,
                testCount = 3,
                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
                updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            )

        testQualityRunSummary =
            QualityRunSummaryDto(
                runId = "run-20260101-001",
                resourceName = "analytics.users",
                status = "COMPLETED",
                overallStatus = "PASSED",
                passedTests = 3,
                failedTests = 0,
                durationSeconds = 2.5,
                startedAt = Instant.parse("2026-01-01T08:00:00Z"),
                completedAt = Instant.parse("2026-01-01T08:00:02Z"),
                executedBy = "system",
            )

        val testDto =
            QualityTestDto(
                name = "user_id_not_null",
                testType = "NOT_NULL",
                description = "Ensure user_id is never null",
                targetColumns = listOf("user_id"),
                config = null,
                enabled = true,
                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            )

        testQualitySpecDetail =
            QualitySpecDetailDto(
                name = "dataset_users_quality",
                resourceName = "analytics.users",
                resourceType = "DATASET",
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "Quality tests for users dataset",
                tags = listOf("domain::analytics", "tier::critical"),
                scheduleCron = "0 0 * * *",
                scheduleTimezone = "UTC",
                enabled = true,
                tests = listOf(testDto),
                recentRuns = listOf(testQualityRunSummary),
                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
                updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            )

        testQualityRunResult =
            QualityRunResultDto(
                runId = "run-20260101-001",
                resourceName = "analytics.users",
                qualitySpecName = "dataset_users_quality",
                status = "COMPLETED",
                overallStatus = "PASSED",
                passedTests = 3,
                failedTests = 0,
                totalTests = 3,
                durationSeconds = 2.5,
                startedAt = Instant.parse("2026-01-01T08:00:00Z"),
                completedAt = Instant.parse("2026-01-01T08:00:02Z"),
                executedBy = "system",
                testResults =
                    listOf(
                        TestResultSummaryDto(
                            testName = "user_id_not_null",
                            testType = "NOT_NULL",
                            status = "PASSED",
                            failedRows = 0L,
                            totalRows = 1000L,
                            executionTimeSeconds = 0.5,
                            errorMessage = null,
                        ),
                    ),
            )
    }

    @Nested
    @DisplayName("GET /api/v1/quality")
    inner class ListQualitySpecs {
        @Test
        @DisplayName("should return empty list when no quality specs exist")
        fun `should return empty list when no quality specs exist`() {
            // Given
            every { qualityService.getQualitySpecs(any(), any(), any(), any()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { qualityService.getQualitySpecs(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("should return quality specs list")
        fun `should return quality specs list`() {
            // Given
            val specs = listOf(testQualitySpec)
            every { qualityService.getQualitySpecs(any(), any(), any(), any()) } returns specs
            every { qualityMapper.toSummaryDto(testQualitySpec) } returns testQualitySpecSummary

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("dataset_users_quality"))
                .andExpect(jsonPath("$[0].resource_name").value("analytics.users"))
                .andExpect(jsonPath("$[0].resource_type").value("DATASET"))
                .andExpect(jsonPath("$[0].owner").value("data-team@example.com"))

            verify(exactly = 1) { qualityService.getQualitySpecs(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("should filter specs by resource type")
        fun `should filter specs by resource type`() {
            // Given
            val resourceType = "DATASET"
            val specs = listOf(testQualitySpec)
            every {
                qualityService.getQualitySpecs(
                    resourceType = resourceType,
                    tag = any(),
                    limit = any(),
                    offset = any(),
                )
            } returns specs
            every { qualityMapper.toSummaryDto(testQualitySpec) } returns testQualitySpecSummary

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/quality")
                        .param("resourceType", resourceType),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                qualityService.getQualitySpecs(
                    resourceType = resourceType,
                    tag = any(),
                    limit = any(),
                    offset = any(),
                )
            }
        }

        @Test
        @DisplayName("should filter specs by tag")
        fun `should filter specs by tag`() {
            // Given
            val tag = "tier::critical"
            val specs = listOf(testQualitySpec)
            every {
                qualityService.getQualitySpecs(
                    resourceType = any(),
                    tag = tag,
                    limit = any(),
                    offset = any(),
                )
            } returns specs
            every { qualityMapper.toSummaryDto(testQualitySpec) } returns testQualitySpecSummary

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/quality")
                        .param("tag", tag),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) {
                qualityService.getQualitySpecs(
                    resourceType = any(),
                    tag = tag,
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
                qualityService.getQualitySpecs(
                    resourceType = any(),
                    tag = any(),
                    limit = limit,
                    offset = offset,
                )
            } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/quality")
                        .param("limit", limit.toString())
                        .param("offset", offset.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                qualityService.getQualitySpecs(
                    resourceType = any(),
                    tag = any(),
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
                    get("/api/v1/quality")
                        .param("limit", "501"),
                ).andExpect(status().is4xxClientError)
        }

        @Test
        @DisplayName("should reject request when offset is negative")
        fun `should reject request when offset is negative`() {
            // When offset is negative, validation should reject the request
            mockMvc
                .perform(
                    get("/api/v1/quality")
                        .param("offset", "-1"),
                ).andExpect(status().is4xxClientError)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quality/{name}")
    inner class GetQualitySpec {
        @Test
        @DisplayName("should return quality spec details for existing spec")
        fun `should return quality spec details for existing spec`() {
            // Given
            val specName = "dataset_users_quality"
            every { qualityService.getQualitySpecOrThrow(specName) } returns testQualitySpec
            every { qualityService.getQualityTests(specName) } returns listOf(testQualityTest)
            every { qualityService.getQualityRuns(specName, limit = 5, offset = 0) } returns listOf(testQualityRun)
            every { qualityMapper.toDetailDto(testQualitySpec, any(), any()) } returns testQualitySpecDetail

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality/$specName"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(specName))
                .andExpect(jsonPath("$.resource_name").value("analytics.users"))
                .andExpect(jsonPath("$.resource_type").value("DATASET"))
                .andExpect(jsonPath("$.owner").value("data-team@example.com"))
                .andExpect(jsonPath("$.tests").isArray())
                .andExpect(jsonPath("$.recent_runs").isArray())

            verify(exactly = 1) { qualityService.getQualitySpecOrThrow(specName) }
            verify(exactly = 1) { qualityService.getQualityRuns(specName, limit = 5, offset = 0) }
        }

        @Test
        @DisplayName("should return 404 when quality spec not found")
        fun `should return 404 when quality spec not found`() {
            // Given
            val specName = "nonexistent_spec"
            every { qualityService.getQualitySpecOrThrow(specName) } throws QualitySpecNotFoundException(specName)

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality/$specName"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { qualityService.getQualitySpecOrThrow(specName) }
        }

        @Test
        @DisplayName("should return quality spec with tests information")
        fun `should return quality spec with tests information`() {
            // Given
            val specName = "dataset_users_quality"
            every { qualityService.getQualitySpecOrThrow(specName) } returns testQualitySpec
            every { qualityService.getQualityTests(specName) } returns listOf(testQualityTest)
            every { qualityService.getQualityRuns(specName, limit = 5, offset = 0) } returns listOf(testQualityRun)
            every { qualityMapper.toDetailDto(testQualitySpec, any(), any()) } returns testQualitySpecDetail

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality/$specName"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.tests").isArray())
                .andExpect(jsonPath("$.tests.length()").value(1))
                .andExpect(jsonPath("$.tests[0].name").value("user_id_not_null"))
                .andExpect(jsonPath("$.tests[0].test_type").value("NOT_NULL"))
        }

        @Test
        @DisplayName("should return quality spec with recent runs information")
        fun `should return quality spec with recent runs information`() {
            // Given
            val specName = "dataset_users_quality"
            every { qualityService.getQualitySpecOrThrow(specName) } returns testQualitySpec
            every { qualityService.getQualityTests(specName) } returns listOf(testQualityTest)
            every { qualityService.getQualityRuns(specName, limit = 5, offset = 0) } returns listOf(testQualityRun)
            every { qualityMapper.toDetailDto(testQualitySpec, any(), any()) } returns testQualitySpecDetail

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality/$specName"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.recent_runs").isArray())
                .andExpect(jsonPath("$.recent_runs.length()").value(1))
                .andExpect(jsonPath("$.recent_runs[0].run_id").value("run-20260101-001"))
                .andExpect(jsonPath("$.recent_runs[0].status").value("COMPLETED"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/quality/test/{resource_name}")
    inner class ExecuteQualityTests {
        @Test
        @DisplayName("should execute quality tests successfully")
        fun `should execute quality tests successfully`() {
            // Given
            val resourceName = "analytics.users"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "dataset_users_quality",
                    testNames = listOf("user_id_not_null"),
                    timeout = 300,
                    executedBy = "test-user",
                )
            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = "dataset_users_quality",
                    testNames = listOf("user_id_not_null"),
                    timeout = 300,
                    executedBy = "test-user",
                )
            } returns testQualityRun
            every { qualityMapper.toRunResultDto(testQualityRun) } returns testQualityRunResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.run_id").value("run-20260101-001"))
                .andExpect(jsonPath("$.resource_name").value("analytics.users"))
                .andExpect(jsonPath("$.quality_spec_name").value("dataset_users_quality"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overall_status").value("PASSED"))
                .andExpect(jsonPath("$.passed_tests").value(3))
                .andExpect(jsonPath("$.failed_tests").value(0))
                .andExpect(jsonPath("$.test_results").isArray())

            verify(exactly = 1) {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = "dataset_users_quality",
                    testNames = listOf("user_id_not_null"),
                    timeout = 300,
                    executedBy = "test-user",
                )
            }
        }

        @Test
        @DisplayName("should execute all tests when no test names specified")
        fun `should execute all tests when no test names specified`() {
            // Given
            val resourceName = "analytics.users"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "dataset_users_quality",
                    testNames = emptyList(),
                    timeout = 300,
                    executedBy = "test-user",
                )
            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = "dataset_users_quality",
                    testNames = null, // empty list is converted to null
                    timeout = 300,
                    executedBy = "test-user",
                )
            } returns testQualityRun
            every { qualityMapper.toRunResultDto(testQualityRun) } returns testQualityRunResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.run_id").exists())

            verify(exactly = 1) {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = "dataset_users_quality",
                    testNames = null,
                    timeout = 300,
                    executedBy = "test-user",
                )
            }
        }

        @Test
        @DisplayName("should use default timeout when not specified")
        fun `should use default timeout when not specified`() {
            // Given
            val resourceName = "analytics.users"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "dataset_users_quality",
                    executedBy = "test-user",
                )
            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = "dataset_users_quality",
                    testNames = null,
                    timeout = 300, // default timeout
                    executedBy = "test-user",
                )
            } returns testQualityRun
            every { qualityMapper.toRunResultDto(testQualityRun) } returns testQualityRunResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
        }

        @Test
        @DisplayName("should return 404 when quality spec not found")
        fun `should return 404 when quality spec not found`() {
            // Given
            val resourceName = "analytics.users"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "nonexistent_spec",
                    executedBy = "test-user",
                )
            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = "nonexistent_spec",
                    testNames = null,
                    timeout = 300,
                    executedBy = "test-user",
                )
            } throws QualitySpecNotFoundException("nonexistent_spec")

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return test results with details")
        fun `should return test results with details`() {
            // Given
            val resourceName = "analytics.users"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "dataset_users_quality",
                    executedBy = "test-user",
                )
            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = any(),
                    testNames = any(),
                    timeout = any(),
                    executedBy = any(),
                )
            } returns testQualityRun
            every { qualityMapper.toRunResultDto(testQualityRun) } returns testQualityRunResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.test_results").isArray())
                .andExpect(jsonPath("$.test_results.length()").value(1))
                .andExpect(jsonPath("$.test_results[0].test_name").value("user_id_not_null"))
                .andExpect(jsonPath("$.test_results[0].test_type").value("NOT_NULL"))
                .andExpect(jsonPath("$.test_results[0].status").value("PASSED"))
                .andExpect(jsonPath("$.test_results[0].failed_rows").value(0))
                .andExpect(jsonPath("$.test_results[0].total_rows").value(1000))
        }

        @Test
        @DisplayName("should reject request when timeout is too small")
        fun `should reject request when timeout is too small`() {
            // Given
            val resourceName = "analytics.users"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "dataset_users_quality",
                    timeout = 0, // below minimum of 1
                    executedBy = "test-user",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().is4xxClientError)
        }

        @Test
        @DisplayName("should reject request when timeout exceeds maximum")
        fun `should reject request when timeout exceeds maximum`() {
            // Given
            val resourceName = "analytics.users"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "dataset_users_quality",
                    timeout = 3601, // above maximum of 3600
                    executedBy = "test-user",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().is4xxClientError)
        }
    }
}
