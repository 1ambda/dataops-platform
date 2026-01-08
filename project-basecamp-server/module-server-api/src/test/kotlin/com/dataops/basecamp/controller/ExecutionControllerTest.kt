package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.domain.projection.execution.QualityTestResultProjection
import com.dataops.basecamp.domain.projection.execution.RenderedExecutionResultProjection
import com.dataops.basecamp.domain.projection.execution.RenderedQualityExecutionResultProjection
import com.dataops.basecamp.domain.service.ExecutionService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper

/**
 * ExecutionController REST API Tests
 *
 * Tests for CLI-rendered execution APIs:
 * - POST /api/v1/execution/datasets/run
 * - POST /api/v1/execution/metrics/run
 * - POST /api/v1/execution/quality/run
 * - POST /api/v1/execution/sql/run
 *
 * Spring Boot 4.x patterns:
 * - @SpringBootTest + @AutoConfigureMockMvc for full integration test
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class ExecutionControllerTest {
    /**
     * Test configuration to enable method-level validation for @Min, @Max, @Size annotations
     * on controller method parameters.
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

    // Test data
    private lateinit var testExecutionResult: RenderedExecutionResultProjection
    private lateinit var testQualityResult: RenderedQualityExecutionResultProjection
    private val testExecutionId = "exec_20260101_120000_abc12345"

    @BeforeEach
    fun setUp() {
        testExecutionResult =
            RenderedExecutionResultProjection(
                executionId = testExecutionId,
                status = ExecutionStatus.COMPLETED,
                rows = listOf(mapOf("id" to 1, "name" to "Alice"), mapOf("id" to 2, "name" to "Bob")),
                rowCount = 2,
                durationSeconds = 1.5,
                renderedSql = "SELECT * FROM users",
                error = null,
            )

        testQualityResult =
            RenderedQualityExecutionResultProjection(
                executionId = testExecutionId,
                status = ExecutionStatus.SUCCESS,
                results =
                    listOf(
                        QualityTestResultProjection(
                            testName = "not_null_check",
                            passed = true,
                            failedCount = 0,
                            failedRows = null,
                            durationMs = 500L,
                        ),
                        QualityTestResultProjection(
                            testName = "unique_check",
                            passed = true,
                            failedCount = 0,
                            failedRows = null,
                            durationMs = 300L,
                        ),
                    ),
                totalTests = 2,
                passedTests = 2,
                failedTests = 0,
                totalDurationMs = 800L,
            )
    }

    @Nested
    @DisplayName("POST /api/v1/execution/datasets/run")
    inner class ExecuteDataset {
        @Test
        @DisplayName("should execute dataset SQL successfully")
        fun `should execute dataset SQL successfully`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users WHERE date = '2026-01-01'",
                    "parameters" to mapOf("date" to "2026-01-01"),
                    "execution_timeout" to 300,
                )

            every { executionService.executeRenderedDatasetSql(any()) } returns testExecutionResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.execution_id").value(testExecutionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.row_count").value(2))
                .andExpect(jsonPath("$.duration_seconds").value(1.5))
                .andExpect(jsonPath("$.rendered_sql").value("SELECT * FROM users"))
                .andExpect(jsonPath("$.rows").isArray)
                .andExpect(jsonPath("$.rows.length()").value(2))

            verify(exactly = 1) { executionService.executeRenderedDatasetSql(any()) }
        }

        @Test
        @DisplayName("should execute with optional transpile metadata")
        fun `should execute with optional transpile metadata`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users",
                    "transpile_source_dialect" to "mysql",
                    "transpile_target_dialect" to "bigquery",
                    "transpile_used_server_policy" to true,
                )

            every { executionService.executeRenderedDatasetSql(any()) } returns testExecutionResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.execution_id").exists())

            verify(exactly = 1) { executionService.executeRenderedDatasetSql(any()) }
        }

        @Test
        @DisplayName("should return 400 when rendered_sql is missing")
        fun `should return 400 when rendered_sql is missing`() {
            // Given
            val request =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when rendered_sql is blank")
        fun `should return 400 when rendered_sql is blank`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_timeout is too small")
        fun `should return 400 when execution_timeout is too small`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users",
                    "execution_timeout" to 0, // Less than @Min(1)
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_timeout exceeds maximum")
        fun `should return 400 when execution_timeout exceeds maximum`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users",
                    "execution_timeout" to 4000, // More than @Max(3600)
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_limit is too small")
        fun `should return 400 when execution_limit is too small`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users",
                    "execution_limit" to 0, // Less than @Min(1)
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_limit exceeds maximum")
        fun `should return 400 when execution_limit exceeds maximum`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users",
                    "execution_limit" to 20000, // More than @Max(10000)
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return error when execution fails")
        fun `should return error when execution fails`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users",
                )
            val errorResult =
                RenderedExecutionResultProjection(
                    executionId = testExecutionId,
                    status = ExecutionStatus.FAILED,
                    rows = null,
                    rowCount = null,
                    durationSeconds = null,
                    renderedSql = "SELECT * FROM users",
                    error = "Table not found: users",
                )

            every { executionService.executeRenderedDatasetSql(any()) } returns errorResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Table not found: users"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/execution/metrics/run")
    inner class ExecuteMetric {
        @Test
        @DisplayName("should execute metric SQL successfully")
        fun `should execute metric SQL successfully`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT COUNT(*) as total FROM orders WHERE date = '2026-01-01'",
                    "parameters" to mapOf("date" to "2026-01-01"),
                )
            val metricResult =
                RenderedExecutionResultProjection(
                    executionId = testExecutionId,
                    status = ExecutionStatus.COMPLETED,
                    rows = listOf(mapOf("total" to 1000)),
                    rowCount = 1,
                    durationSeconds = 2.0,
                    renderedSql = "SELECT COUNT(*) as total FROM orders WHERE date = '2026-01-01'",
                    error = null,
                )

            every { executionService.executeRenderedMetricSql(any()) } returns metricResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/metrics/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.execution_id").value(testExecutionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.row_count").value(1))
                .andExpect(jsonPath("$.rows[0].total").value(1000))

            verify(exactly = 1) { executionService.executeRenderedMetricSql(any()) }
        }

        @Test
        @DisplayName("should return 400 when rendered_sql is missing")
        fun `should return 400 when rendered_sql is missing`() {
            // Given
            val request =
                mapOf(
                    "parameters" to mapOf("date" to "2026-01-01"),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/metrics/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_timeout is invalid")
        fun `should return 400 when execution_timeout is invalid`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT COUNT(*) FROM orders",
                    "execution_timeout" to -1, // Invalid
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/metrics/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should accept optional resource_name and original_spec")
        fun `should accept optional resource_name and original_spec`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT COUNT(*) FROM orders",
                    "resource_name" to "orders_count",
                    "original_spec" to
                        mapOf(
                            "name" to "orders_count",
                            "version" to "1.0.0",
                        ),
                )

            every { executionService.executeRenderedMetricSql(any()) } returns testExecutionResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/metrics/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

            verify(exactly = 1) { executionService.executeRenderedMetricSql(any()) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/execution/quality/run")
    inner class ExecuteQuality {
        @Test
        @DisplayName("should execute quality tests successfully")
        fun `should execute quality tests successfully`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                    "tests" to
                        listOf(
                            mapOf(
                                "name" to "not_null_check",
                                "type" to "not_null",
                                "rendered_sql" to "SELECT * FROM users WHERE id IS NULL",
                            ),
                            mapOf(
                                "name" to "unique_check",
                                "type" to "unique",
                                "rendered_sql" to "SELECT id, COUNT(*) FROM users GROUP BY id HAVING COUNT(*) > 1",
                            ),
                        ),
                )

            every { executionService.executeRenderedQualitySql(any()) } returns testQualityResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.execution_id").value(testExecutionId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.total_tests").value(2))
                .andExpect(jsonPath("$.passed_tests").value(2))
                .andExpect(jsonPath("$.failed_tests").value(0))
                .andExpect(jsonPath("$.results").isArray)
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].test_name").value("not_null_check"))
                .andExpect(jsonPath("$.results[0].passed").value(true))

            verify(exactly = 1) { executionService.executeRenderedQualitySql(any()) }
        }

        @Test
        @DisplayName("should return partial failure when some tests fail")
        fun `should return partial failure when some tests fail`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                    "tests" to
                        listOf(
                            mapOf(
                                "name" to "not_null_check",
                                "type" to "not_null",
                                "rendered_sql" to "SELECT * FROM users WHERE id IS NULL",
                            ),
                        ),
                )
            val failedResult =
                RenderedQualityExecutionResultProjection(
                    executionId = testExecutionId,
                    status = ExecutionStatus.FAILED,
                    results =
                        listOf(
                            QualityTestResultProjection(
                                testName = "not_null_check",
                                passed = false,
                                failedCount = 5,
                                failedRows =
                                    listOf(
                                        mapOf<String, Any>("id" to "null", "name" to "Invalid"),
                                    ),
                                durationMs = 500L,
                            ),
                        ),
                    totalTests = 1,
                    passedTests = 0,
                    failedTests = 1,
                    totalDurationMs = 500L,
                )

            every { executionService.executeRenderedQualitySql(any()) } returns failedResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failed_tests").value(1))
                .andExpect(jsonPath("$.results[0].passed").value(false))
                .andExpect(jsonPath("$.results[0].failed_count").value(5))
                .andExpect(jsonPath("$.results[0].failed_rows").isArray)
        }

        @Test
        @DisplayName("should return 400 when resource_name is missing")
        fun `should return 400 when resource_name is missing`() {
            // Given
            val request =
                mapOf(
                    "tests" to
                        listOf(
                            mapOf(
                                "name" to "not_null_check",
                                "type" to "not_null",
                                "rendered_sql" to "SELECT * FROM users WHERE id IS NULL",
                            ),
                        ),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when tests list is empty")
        fun `should return 400 when tests list is empty`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                    "tests" to emptyList<Map<String, Any>>(),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when tests field is missing")
        fun `should return 400 when tests field is missing`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when test name is missing")
        fun `should return 400 when test name is missing`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                    "tests" to
                        listOf(
                            mapOf(
                                "type" to "not_null",
                                "rendered_sql" to "SELECT * FROM users WHERE id IS NULL",
                            ),
                        ),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when test type is missing")
        fun `should return 400 when test type is missing`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                    "tests" to
                        listOf(
                            mapOf(
                                "name" to "not_null_check",
                                "rendered_sql" to "SELECT * FROM users WHERE id IS NULL",
                            ),
                        ),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when rendered_sql is missing in test")
        fun `should return 400 when rendered_sql is missing in test`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                    "tests" to
                        listOf(
                            mapOf(
                                "name" to "not_null_check",
                                "type" to "not_null",
                            ),
                        ),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_timeout exceeds maximum")
        fun `should return 400 when execution_timeout exceeds maximum`() {
            // Given
            val request =
                mapOf(
                    "resource_name" to "users_dataset",
                    "tests" to
                        listOf(
                            mapOf(
                                "name" to "not_null_check",
                                "type" to "not_null",
                                "rendered_sql" to "SELECT * FROM users WHERE id IS NULL",
                            ),
                        ),
                    "execution_timeout" to 5000, // More than @Max(3600)
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/quality/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/execution/sql/run")
    inner class ExecuteSql {
        @Test
        @DisplayName("should execute ad-hoc SQL successfully")
        fun `should execute ad-hoc SQL successfully`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users LIMIT 10",
                    "parameters" to mapOf("limit" to 10),
                )

            every { executionService.executeRenderedAdHocSql(any()) } returns testExecutionResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.execution_id").value(testExecutionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rendered_sql").value("SELECT * FROM users"))

            verify(exactly = 1) { executionService.executeRenderedAdHocSql(any()) }
        }

        @Test
        @DisplayName("should accept target_dialect parameter")
        fun `should accept target_dialect parameter`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "target_dialect" to "trino",
                )

            every { executionService.executeRenderedAdHocSql(any()) } returns testExecutionResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

            verify(exactly = 1) { executionService.executeRenderedAdHocSql(any()) }
        }

        @Test
        @DisplayName("should return 400 when sql is missing")
        fun `should return 400 when sql is missing`() {
            // Given
            val request =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when sql is blank")
        fun `should return 400 when sql is blank`() {
            // Given
            val request =
                mapOf(
                    "sql" to "",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when sql is whitespace only")
        fun `should return 400 when sql is whitespace only`() {
            // Given
            val request =
                mapOf(
                    "sql" to "   ",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_timeout is too small")
        fun `should return 400 when execution_timeout is too small`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "execution_timeout" to 0,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_timeout exceeds maximum")
        fun `should return 400 when execution_timeout exceeds maximum`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "execution_timeout" to 7200, // More than @Max(3600)
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when execution_limit exceeds maximum")
        fun `should return 400 when execution_limit exceeds maximum`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM users",
                    "execution_limit" to 50000, // More than @Max(10000)
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return error response when execution fails")
        fun `should return error response when execution fails`() {
            // Given
            val request =
                mapOf(
                    "sql" to "SELECT * FROM nonexistent_table",
                )
            val errorResult =
                RenderedExecutionResultProjection(
                    executionId = testExecutionId,
                    status = ExecutionStatus.FAILED,
                    rows = null,
                    rowCount = null,
                    durationSeconds = null,
                    renderedSql = "SELECT * FROM nonexistent_table",
                    error = "Table not found: nonexistent_table",
                )

            every { executionService.executeRenderedAdHocSql(any()) } returns errorResult

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/sql/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Table not found: nonexistent_table"))
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("should return proper error structure for invalid JSON")
        fun `should return proper error structure for invalid JSON`() {
            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return error for missing content type")
        fun `should return error for missing content type`() {
            // Given
            val request = """{"rendered_sql": "SELECT * FROM users"}"""

            // When & Then
            // Note: When content-type is missing, Spring defaults to application/octet-stream
            // which is not supported, causing an error (handled by GlobalExceptionHandler)
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .content(request),
                ).andExpect(status().is5xxServerError)
        }

        @Test
        @DisplayName("should handle service exception gracefully")
        fun `should handle service exception gracefully`() {
            // Given
            val request =
                mapOf(
                    "rendered_sql" to "SELECT * FROM users",
                )

            every {
                executionService.executeRenderedDatasetSql(any())
            } throws RuntimeException("Database connection failed")

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/execution/datasets/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isInternalServerError)
        }
    }
}
