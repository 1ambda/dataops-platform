package com.github.lambda.controller

import com.fasterxml.jackson.databind.json.JsonMapper
import com.github.lambda.common.exception.QualityRunNotFoundException
import com.github.lambda.common.exception.QualitySpecNotFoundException
import com.github.lambda.domain.model.quality.QualityRunEntity
import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.quality.RunStatus
import com.github.lambda.domain.model.quality.TestStatus
import com.github.lambda.domain.service.QualityService
import com.github.lambda.dto.quality.ExecuteQualityTestRequest
import com.github.lambda.dto.quality.QualityRunResultDto
import com.github.lambda.dto.quality.QualitySpecDetailDto
import com.github.lambda.dto.quality.QualitySpecSummaryDto
import com.github.lambda.mapper.QualityMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDateTime

/**
 * QualityController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @SpringBootTest + @AutoConfigureMockMvc: Integration test (multi-module project compatible)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class QualityControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @MockkBean(relaxed = true)
    private lateinit var qualityService: QualityService

    @MockkBean(relaxed = true)
    private lateinit var qualityMapper: QualityMapper

    private lateinit var testQualitySpecEntity: QualitySpecEntity
    private lateinit var testQualitySpecSummaryDto: QualitySpecSummaryDto
    private lateinit var testQualitySpecDetailDto: QualitySpecDetailDto
    private lateinit var testQualityRunEntity: QualityRunEntity
    private lateinit var testQualityRunResultDto: QualityRunResultDto

    @BeforeEach
    fun setUp() {
        testQualitySpecEntity =
            QualitySpecEntity(
                name = "test_quality_spec",
                resourceName = "test_catalog.test_schema.test_table",
                resourceType = ResourceType.DATASET,
                owner = "test@example.com",
                team = "data-team",
                description = "Test quality specification",
                tags = mutableSetOf("test", "quality"),
                enabled = true,
            )

        testQualitySpecSummaryDto =
            QualitySpecSummaryDto(
                name = "test_quality_spec",
                resourceName = "test_catalog.test_schema.test_table",
                resourceType = "DATASET",
                owner = "test@example.com",
                team = "data-team",
                description = "Test quality specification",
                tags = listOf("quality", "test"),
                scheduleCron = null,
                scheduleTimezone = "UTC",
                enabled = true,
                testCount = 2,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        testQualitySpecDetailDto =
            QualitySpecDetailDto(
                name = "test_quality_spec",
                resourceName = "test_catalog.test_schema.test_table",
                resourceType = "DATASET",
                owner = "test@example.com",
                team = "data-team",
                description = "Test quality specification",
                tags = listOf("quality", "test"),
                scheduleCron = null,
                scheduleTimezone = "UTC",
                enabled = true,
                tests = emptyList(),
                recentRuns = emptyList(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        testQualityRunEntity =
            QualityRunEntity(
                runId = "test_run_20250102_123456_abcd1234",
                resourceName = "test_catalog.test_schema.test_table",
                status = RunStatus.COMPLETED,
                overallStatus = TestStatus.PASSED,
                startedAt = Instant.now().minusSeconds(60),
                completedAt = Instant.now(),
                durationSeconds = 60.0,
                passedTests = 2,
                failedTests = 0,
                executedBy = "test@example.com",
            )

        testQualityRunResultDto =
            QualityRunResultDto(
                runId = "test_run_20250102_123456_abcd1234",
                resourceName = "test_catalog.test_schema.test_table",
                qualitySpecName = "test_quality_spec",
                status = "COMPLETED",
                overallStatus = "PASSED",
                passedTests = 2,
                failedTests = 0,
                totalTests = 2,
                durationSeconds = 60.0,
                startedAt = Instant.now().minusSeconds(60),
                completedAt = Instant.now(),
                executedBy = "test@example.com",
                testResults = emptyList(),
            )
    }

    @Nested
    @DisplayName("GET /api/v1/quality")
    inner class ListQualitySpecs {
        @Test
        @DisplayName("should return empty list when no quality specs exist")
        fun `should return empty list when no quality specs exist`() {
            // Given
            every { qualityService.getQualitySpecs(null, null, 50, 0) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { qualityService.getQualitySpecs(null, null, 50, 0) }
        }

        @Test
        @DisplayName("should return quality specs list")
        fun `should return quality specs list`() {
            // Given
            val specs = listOf(testQualitySpecEntity)
            every { qualityService.getQualitySpecs(null, null, 50, 0) } returns specs
            every { qualityMapper.toSummaryDto(testQualitySpecEntity) } returns testQualitySpecSummaryDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("test_quality_spec"))
                .andExpect(jsonPath("$[0].owner").value("test@example.com"))

            verify(exactly = 1) { qualityService.getQualitySpecs(null, null, 50, 0) }
            verify(exactly = 1) { qualityMapper.toSummaryDto(testQualitySpecEntity) }
        }

        @Test
        @DisplayName("should filter quality specs by resourceType")
        fun `should filter quality specs by resourceType`() {
            // Given
            val resourceType = "DATASET"
            val specs = listOf(testQualitySpecEntity)
            every { qualityService.getQualitySpecs(resourceType, null, 50, 0) } returns specs
            every { qualityMapper.toSummaryDto(testQualitySpecEntity) } returns testQualitySpecSummaryDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/quality")
                        .param("resourceType", resourceType),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { qualityService.getQualitySpecs(resourceType, null, 50, 0) }
        }

        @Test
        @DisplayName("should filter quality specs by tag")
        fun `should filter quality specs by tag`() {
            // Given
            val tag = "test"
            val specs = listOf(testQualitySpecEntity)
            every { qualityService.getQualitySpecs(null, tag, 50, 0) } returns specs
            every { qualityMapper.toSummaryDto(testQualitySpecEntity) } returns testQualitySpecSummaryDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/quality")
                        .param("tag", tag),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { qualityService.getQualitySpecs(null, tag, 50, 0) }
        }

        @Test
        @DisplayName("should apply limit and offset")
        fun `should apply limit and offset`() {
            // Given
            val limit = 10
            val offset = 5
            every { qualityService.getQualitySpecs(null, null, limit, offset) } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/quality")
                        .param("limit", limit.toString())
                        .param("offset", offset.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) { qualityService.getQualitySpecs(null, null, limit, offset) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quality/{name}")
    inner class GetQualitySpec {
        @Test
        @DisplayName("should return quality spec by name")
        fun `should return quality spec by name`() {
            // Given
            val name = "test_quality_spec"
            val recentRuns = listOf(testQualityRunEntity)
            every { qualityService.getQualitySpecOrThrow(name) } returns testQualitySpecEntity
            every { qualityService.getQualityRuns(name, limit = 5, offset = 0) } returns recentRuns
            every { qualityMapper.toDetailDto(testQualitySpecEntity, recentRuns) } returns testQualitySpecDetailDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/quality/$name"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.owner").value("test@example.com"))
                .andExpect(jsonPath("$.resourceType").value("DATASET"))

            verify(exactly = 1) { qualityService.getQualitySpecOrThrow(name) }
            verify(exactly = 1) { qualityService.getQualityRuns(name, limit = 5, offset = 0) }
            verify(exactly = 1) { qualityMapper.toDetailDto(testQualitySpecEntity, recentRuns) }
        }

        @Test
        @DisplayName("should throw QualitySpecNotFoundException when quality spec not found")
        fun `should throw QualitySpecNotFoundException when quality spec not found`() {
            // Given
            val name = "nonexistent_quality_spec"
            every { qualityService.getQualitySpecOrThrow(name) } throws QualitySpecNotFoundException(name)

            // When & Then
            // Controller throws QualitySpecNotFoundException, caught by GlobalExceptionHandler in production
            // In MockMvc tests, we verify the exception is thrown
            val exception =
                org.junit.jupiter.api.assertThrows<Exception> {
                    mockMvc.perform(get("/api/v1/quality/$name")).andReturn()
                }

            assertThat(exception.cause).isInstanceOf(QualitySpecNotFoundException::class.java)
            verify(exactly = 1) { qualityService.getQualitySpecOrThrow(name) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/quality/test/{resource_name}")
    inner class ExecuteQualityTests {
        @Test
        @DisplayName("should execute quality tests successfully")
        fun `should execute quality tests successfully`() {
            // Given
            val resourceName = "test_catalog.test_schema.test_table"
            val request =
                ExecuteQualityTestRequest(
                    qualitySpecName = "test_quality_spec",
                    testNames = listOf("not_null_test", "unique_test"),
                    timeout = 300,
                    executedBy = "test@example.com",
                )

            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = request.qualitySpecName,
                    testNames = request.testNames,
                    timeout = request.timeout,
                    executedBy = request.executedBy!!,
                )
            } returns testQualityRunEntity

            every { qualityMapper.toRunResultDto(testQualityRunEntity) } returns testQualityRunResultDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.run_id").value("test_run_20250102_123456_abcd1234"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overall_status").value("PASSED"))

            verify(exactly = 1) {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = request.qualitySpecName,
                    testNames = request.testNames,
                    timeout = request.timeout,
                    executedBy = request.executedBy!!,
                )
            }
        }

        @Test
        @DisplayName("should execute quality tests with minimal request")
        fun `should execute quality tests with minimal request`() {
            // Given
            val resourceName = "test_catalog.test_schema.test_table"
            val request = ExecuteQualityTestRequest()

            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = null,
                    testNames = null,
                    timeout = 300,
                    executedBy = any(), // Will use SecurityContext.getCurrentUsername()
                )
            } returns testQualityRunEntity

            every { qualityMapper.toRunResultDto(testQualityRunEntity) } returns testQualityRunResultDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = null,
                    testNames = null,
                    timeout = 300,
                    executedBy = any(),
                )
            }
        }

        @Test
        @DisplayName("should throw QualitySpecNotFoundException when no specs found for resource")
        fun `should throw QualitySpecNotFoundException when no specs found for resource`() {
            // Given
            val resourceName = "nonexistent_catalog.schema.table"
            val request = ExecuteQualityTestRequest()

            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = any(),
                    testNames = any(),
                    timeout = any(),
                    executedBy = any(),
                )
            } throws QualitySpecNotFoundException("No quality specs found for resource: $resourceName")

            // When & Then
            // Controller throws QualitySpecNotFoundException, caught by GlobalExceptionHandler in production
            val exception =
                org.junit.jupiter.api.assertThrows<Exception> {
                    mockMvc
                        .perform(
                            post("/api/v1/quality/test/$resourceName")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(request)),
                        ).andReturn()
                }

            assertThat(exception.cause).isInstanceOf(QualitySpecNotFoundException::class.java)
        }

        @Test
        @DisplayName("should return 400 when timeout is less than 1")
        fun `should return 400 when timeout is less than 1`() {
            // Given
            val resourceName = "test_catalog.test_schema.test_table"
            val invalidRequest =
                mapOf(
                    "timeout" to 0, // Invalid - must be at least 1
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when timeout exceeds maximum")
        fun `should return 400 when timeout exceeds maximum`() {
            // Given
            val resourceName = "test_catalog.test_schema.test_table"
            val invalidRequest =
                mapOf(
                    "timeout" to 3601, // Invalid - must not exceed 3600
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when testNames exceeds maximum limit")
        fun `should return 400 when testNames exceeds maximum limit`() {
            // Given
            val resourceName = "test_catalog.test_schema.test_table"
            val tooManyTestNames = (1..60).map { "test_$it" } // 60 test names, limit is 50
            val invalidRequest =
                mapOf(
                    "test_names" to tooManyTestNames,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        @Test
        @DisplayName("should handle complete list-get-execute flow")
        fun `should handle complete list-get-execute flow`() {
            // Given - Setup for list
            val specs = listOf(testQualitySpecEntity)
            every { qualityService.getQualitySpecs(null, null, 50, 0) } returns specs
            every { qualityMapper.toSummaryDto(testQualitySpecEntity) } returns testQualitySpecSummaryDto

            // When & Then - List
            mockMvc
                .perform(get("/api/v1/quality"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            // Given - Setup for get
            val recentRuns = listOf(testQualityRunEntity)
            every { qualityService.getQualitySpecOrThrow("test_quality_spec") } returns testQualitySpecEntity
            every { qualityService.getQualityRuns("test_quality_spec", limit = 5, offset = 0) } returns recentRuns
            every { qualityMapper.toDetailDto(testQualitySpecEntity, recentRuns) } returns testQualitySpecDetailDto

            // When & Then - Get
            mockMvc
                .perform(get("/api/v1/quality/test_quality_spec"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("test_quality_spec"))

            // Given - Setup for execute
            val resourceName = "test_catalog.test_schema.test_table"
            val executeRequest = ExecuteQualityTestRequest()
            every {
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = any(),
                    testNames = any(),
                    timeout = any(),
                    executedBy = any(),
                )
            } returns testQualityRunEntity
            every { qualityMapper.toRunResultDto(testQualityRunEntity) } returns testQualityRunResultDto

            // When & Then - Execute
            mockMvc
                .perform(
                    post("/api/v1/quality/test/$resourceName")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(executeRequest)),
                ).andExpect(status().isOk)

            // Verify all calls
            verify(exactly = 1) { qualityService.getQualitySpecs(null, null, 50, 0) }
            verify(exactly = 1) { qualityService.getQualitySpecOrThrow("test_quality_spec") }
            verify(exactly = 1) { qualityService.getQualityRuns("test_quality_spec", limit = 5, offset = 0) }
        }
    }
}
