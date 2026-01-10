package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.common.exception.InvalidDownloadTokenException
import com.dataops.basecamp.common.exception.QueryEngineNotSupportedException
import com.dataops.basecamp.common.exception.RateLimitExceededException
import com.dataops.basecamp.common.exception.ResultNotFoundException
import com.dataops.basecamp.domain.projection.execution.CurrentUsageProjection
import com.dataops.basecamp.domain.projection.execution.ExecutionPolicyProjection
import com.dataops.basecamp.domain.projection.execution.QueryExecutionResult
import com.dataops.basecamp.domain.projection.execution.RateLimitsProjection
import com.dataops.basecamp.domain.service.ExecutionService
import com.dataops.basecamp.domain.service.ResultStorageService
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * RunController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only (faster than full integration test)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler for proper security and exception handling
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class RunControllerTest {
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
    private lateinit var executionService: ExecutionService

    @MockkBean(relaxed = true)
    private lateinit var resultStorageService: ResultStorageService

    // Test data
    private lateinit var testPolicy: ExecutionPolicyProjection
    private lateinit var testExecutionResult: QueryExecutionResult
    private val testQueryId = "adhoc_20260101_120000_abc12345"
    private val testUserId = "1" // Default mock user ID from MockAuthenticationFilter

    @BeforeEach
    fun setUp() {
        // Setup test policy (using values from RUN_FEATURE.md spec)
        testPolicy =
            ExecutionPolicyProjection(
                maxQueryDurationSeconds = 1800, // 30 min per spec
                maxResultRows = 10000,
                maxResultSizeMb = 100,
                allowedEngines = listOf("bigquery", "trino"),
                allowedFileTypes = listOf("csv"),
                maxFileSizeMb = 10,
                rateLimits =
                    RateLimitsProjection(
                        queriesPerHour = 50, // Per spec: 50/hour
                        queriesPerDay = 200, // Per spec: 200/day
                    ),
                currentUsage =
                    CurrentUsageProjection(
                        queriesToday = 12, // Example usage
                        queriesThisHour = 3,
                    ),
            )

        // Setup test execution result
        testExecutionResult =
            QueryExecutionResult(
                executionId = testQueryId,
                status = ExecutionStatus.SUCCESS,
                rows =
                    listOf(
                        mapOf("id" to 1, "name" to "Alice"),
                        mapOf("id" to 2, "name" to "Bob"),
                    ),
                rowCount = 100,
                schema = null,
                executionTimeMs = 1500L,
                transpiledSql = "SELECT * FROM users",
                error = null,
            )
    }

    @Nested
    @DisplayName("GET /api/v1/run/policy")
    inner class GetPolicy {
        @Test
        @DisplayName("should return execution policy with correct structure")
        fun `should return execution policy with correct structure`() {
            // Given
            every { executionService.getExecutionPolicy(testUserId) } returns testPolicy

            // When & Then
            mockMvc
                .perform(get("/api/v1/run/policy"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.maxQueryDurationSeconds").value(1800))
                .andExpect(jsonPath("$.maxResultRows").value(10000))
                .andExpect(jsonPath("$.maxResultSizeMb").value(100))
                .andExpect(jsonPath("$.allowedEngines").isArray())
                .andExpect(jsonPath("$.allowedEngines.length()").value(2))
                .andExpect(jsonPath("$.allowedEngines[0]").value("bigquery"))
                .andExpect(jsonPath("$.allowedEngines[1]").value("trino"))
                .andExpect(jsonPath("$.allowedFileTypes").isArray())
                .andExpect(jsonPath("$.allowedFileTypes[0]").value("csv"))
                .andExpect(jsonPath("$.maxFileSizeMb").value(10))
                .andExpect(jsonPath("$.rateLimits.queriesPerHour").value(50))
                .andExpect(jsonPath("$.rateLimits.queriesPerDay").value(200))
                .andExpect(jsonPath("$.currentUsage.queriesToday").value(12))
                .andExpect(jsonPath("$.currentUsage.queriesThisHour").value(3))

            verify(exactly = 1) { executionService.getExecutionPolicy(testUserId) }
        }

        @Test
        @DisplayName("should return policy with zero usage for new user")
        fun `should return policy with zero usage for new user`() {
            // Given
            val newUserPolicy =
                testPolicy.copy(
                    currentUsage =
                        CurrentUsageProjection(
                            queriesToday = 0,
                            queriesThisHour = 0,
                        ),
                )
            every { executionService.getExecutionPolicy(testUserId) } returns newUserPolicy

            // When & Then
            mockMvc
                .perform(get("/api/v1/run/policy"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.currentUsage.queriesToday").value(0))
                .andExpect(jsonPath("$.currentUsage.queriesThisHour").value(0))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/run/execute")
    inner class ExecuteSQL {
        @Test
        @DisplayName("should return VALIDATED status for dry run")
        fun `should return VALIDATED status for dry run`() {
            // Given
            val dryRunResult =
                QueryExecutionResult(
                    executionId = "",
                    status = ExecutionStatus.VALIDATED,
                    rows = emptyList(),
                    rowCount = 0,
                    schema = null,
                    executionTimeMs = 500L,
                    transpiledSql = "SELECT * FROM users WHERE date = '2026-01-01'",
                    error = null,
                )
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users WHERE date = {date}",
                    "engine" to "bigquery",
                    "parameters" to mapOf("date" to "2026-01-01"),
                    "dryRun" to true,
                )

            every {
                executionService.executeRawSql(any())
            } returns dryRunResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.renderedSql").value("SELECT * FROM users WHERE date = '2026-01-01'"))

            verify(exactly = 1) {
                executionService.executeRawSql(any())
            }
        }

        @Test
        @DisplayName("should return COMPLETED status for actual execution")
        fun `should return COMPLETED status for actual execution`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "engine" to "bigquery",
                    "downloadFormat" to "csv",
                )
            val downloadUrls = mapOf("csv" to "/api/v1/run/results/$testQueryId/download?format=csv&token=xyz")

            every {
                executionService.executeRawSql(any())
            } returns testExecutionResult
            every {
                resultStorageService.storeResults(
                    queryId = testQueryId,
                    rows = any(),
                    downloadFormat = "csv",
                )
            } returns downloadUrls

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.queryId").value(testQueryId))
                .andExpect(jsonPath("$.rowsReturned").value(100))
                .andExpect(jsonPath("$.downloadUrls.csv").exists())

            verify(exactly = 1) {
                resultStorageService.storeResults(
                    queryId = testQueryId,
                    rows = any(),
                    downloadFormat = "csv",
                )
            }
        }

        @Test
        @DisplayName("should substitute parameters correctly")
        fun `should substitute parameters correctly`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users WHERE date = {date} AND status = {status}",
                    "engine" to "bigquery",
                    "parameters" to mapOf("date" to "2026-01-01", "status" to "active"),
                )
            val resultWithParams =
                testExecutionResult.copy(
                    transpiledSql = "SELECT * FROM users WHERE date = '2026-01-01' AND status = 'active'",
                )

            every {
                executionService.executeRawSql(any())
            } returns resultWithParams
            every { resultStorageService.storeResults(any(), any(), any()) } returns emptyMap()

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.renderedSql").value(
                        "SELECT * FROM users WHERE date = '2026-01-01' AND status = 'active'",
                    ),
                )
        }

        @Test
        @DisplayName("should return 400 for invalid engine")
        fun `should return 400 for invalid engine`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "engine" to "mysql", // Invalid engine
                )

            every {
                executionService.executeRawSql(any())
            } throws
                QueryEngineNotSupportedException(
                    engine = "mysql",
                    allowedEngines = listOf("bigquery", "trino"),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for empty SQL")
        fun `should return 400 for empty SQL`() {
            // Given - validation should catch empty SQL
            val request =
                mapOf(
                    "sql" to "",
                    "engine" to "bigquery",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 429 for rate limit exceeded")
        fun `should return 429 for rate limit exceeded`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "engine" to "bigquery",
                )

            every {
                executionService.executeRawSql(any())
            } throws
                RateLimitExceededException(
                    limitType = "queries_per_hour",
                    limit = 50, // Per spec: 50/hour
                    currentUsage = 50,
                    resetAt = LocalDateTime.now().plusMinutes(30),
                )

            // When & Then - Per RUN_FEATURE.md spec, rate limit returns 429 Too Many Requests
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isTooManyRequests)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
        }

        @Test
        @DisplayName("should use default engine when not specified")
        fun `should use default engine when not specified`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                )

            every {
                executionService.executeRawSql(any())
            } returns testExecutionResult
            every { resultStorageService.storeResults(any(), any(), any()) } returns emptyMap()

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                executionService.executeRawSql(any())
            }
        }

        @Test
        @DisplayName("should not store results for dry run")
        fun `should not store results for dry run`() {
            // Given
            val dryRunResult =
                QueryExecutionResult(
                    executionId = "",
                    status = ExecutionStatus.VALIDATED,
                    rows = emptyList(),
                    rowCount = 0,
                    schema = null,
                    executionTimeMs = 300L,
                    transpiledSql = "SELECT * FROM users",
                    error = null,
                )
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "dryRun" to true,
                )

            every { executionService.executeRawSql(any()) } returns dryRunResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

            verify(exactly = 0) { resultStorageService.storeResults(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/run/results/{queryId}/download")
    inner class DownloadResult {
        @Test
        @DisplayName("should return CSV file for valid request")
        fun `should return CSV file for valid request`() {
            // Given
            val csvContent = "id,name\n1,Alice\n2,Bob".toByteArray()
            every {
                resultStorageService.getResultForDownload(testQueryId, "csv", "valid_token")
            } returns csvContent

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/run/results/$testQueryId/download")
                        .param("format", "csv")
                        .param("token", "valid_token"),
                ).andExpect(status().isOk)
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"result.csv\""))
                .andExpect(header().string("Content-Length", csvContent.size.toString()))
                .andExpect(content().bytes(csvContent))

            verify(exactly = 1) { resultStorageService.getResultForDownload(testQueryId, "csv", "valid_token") }
        }

        @Test
        @DisplayName("should return 400 for invalid token")
        fun `should return 400 for invalid token`() {
            // Given
            every {
                resultStorageService.getResultForDownload(testQueryId, "csv", "invalid_token")
            } throws InvalidDownloadTokenException(testQueryId)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/run/results/$testQueryId/download")
                        .param("format", "csv")
                        .param("token", "invalid_token"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 404 for missing result")
        fun `should return 404 for missing result`() {
            // Given - Per RUN_FEATURE.md spec, missing result returns 404 Not Found
            every {
                resultStorageService.getResultForDownload(testQueryId, "csv", "valid_token")
            } throws ResultNotFoundException(testQueryId)

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/run/results/$testQueryId/download")
                        .param("format", "csv")
                        .param("token", "valid_token"),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESULT_NOT_FOUND"))
        }

        @Test
        @DisplayName("should require format parameter")
        fun `should require format parameter`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/run/results/$testQueryId/download")
                        .param("token", "valid_token"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should require token parameter")
        fun `should require token parameter`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/run/results/$testQueryId/download")
                        .param("format", "csv"),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("should return proper error structure for business exceptions")
        fun `should return proper error structure for business exceptions`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "engine" to "mysql",
                )

            every {
                executionService.executeRawSql(any())
            } throws
                QueryEngineNotSupportedException(
                    engine = "mysql",
                    allowedEngines = listOf("bigquery", "trino"),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/run/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists())
        }
    }
}
