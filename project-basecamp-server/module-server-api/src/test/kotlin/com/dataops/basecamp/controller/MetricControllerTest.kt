package com.dataops.basecamp.controller

import com.dataops.basecamp.common.exception.MetricAlreadyExistsException
import com.dataops.basecamp.common.exception.MetricNotFoundException
import com.dataops.basecamp.domain.command.metric.CreateMetricCommand
import com.dataops.basecamp.domain.entity.metric.MetricEntity
import com.dataops.basecamp.domain.projection.metric.MetricExecutionProjection
import com.dataops.basecamp.domain.service.MetricExecutionService
import com.dataops.basecamp.domain.service.MetricService
import com.dataops.basecamp.dto.metric.CreateMetricRequest
import com.dataops.basecamp.dto.metric.MetricExecutionResultDto
import com.dataops.basecamp.dto.metric.MetricResponse
import com.dataops.basecamp.dto.metric.RunMetricRequest
import com.dataops.basecamp.mapper.MetricMapper
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * MetricController REST API Tests
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
class MetricControllerTest {
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
    private lateinit var metricService: MetricService

    @MockkBean(relaxed = true)
    private lateinit var metricExecutionService: MetricExecutionService

    @MockkBean(relaxed = true)
    private lateinit var metricMapper: MetricMapper

    private lateinit var testMetricEntity: MetricEntity
    private lateinit var testMetricResponse: MetricResponse
    private lateinit var testMetricListResponse: MetricResponse
    private lateinit var testExecutionResult: MetricExecutionProjection
    private lateinit var testExecutionResultDto: MetricExecutionResultDto

    @BeforeEach
    fun setUp() {
        testMetricEntity =
            MetricEntity(
                name = "test_catalog.test_schema.test_metric",
                owner = "test@example.com",
                team = "data-team",
                description = "Test metric description",
                sql = "SELECT COUNT(*) FROM users",
                sourceTable = "users",
                tags = mutableSetOf("test", "user"),
                dependencies = mutableSetOf("users"),
            )

        testMetricResponse =
            MetricResponse(
                name = "test_catalog.test_schema.test_metric",
                type = "Metric",
                owner = "test@example.com",
                team = "data-team",
                description = "Test metric description",
                tags = listOf("test", "user"),
                sql = "SELECT COUNT(*) FROM users",
                sourceTable = "users",
                dependencies = listOf("users"),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        // List response without SQL and dependencies (for list view)
        testMetricListResponse =
            MetricResponse(
                name = "test_catalog.test_schema.test_metric",
                type = "Metric",
                owner = "test@example.com",
                team = "data-team",
                description = "Test metric description",
                tags = listOf("test", "user"),
                sql = null,
                sourceTable = "users",
                dependencies = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        testExecutionResult =
            MetricExecutionProjection(
                rows = listOf(mapOf("count" to 100)),
                rowCount = 1,
                durationSeconds = 0.5,
                renderedSql = "SELECT COUNT(*) FROM users",
            )

        testExecutionResultDto =
            MetricExecutionResultDto(
                rows = listOf(mapOf("count" to 100)),
                rowCount = 1,
                durationSeconds = 0.5,
                renderedSql = "SELECT COUNT(*) FROM users",
            )
    }

    @Nested
    @DisplayName("GET /api/v1/metrics")
    inner class ListMetrics {
        @Test
        @DisplayName("should return empty list when no metrics exist")
        fun `should return empty list when no metrics exist`() {
            // Given
            every { metricService.listMetrics(null, null, null, 50, 0) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/metrics"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0))

            verify(exactly = 1) { metricService.listMetrics(null, null, null, 50, 0) }
        }

        @Test
        @DisplayName("should return metrics list")
        fun `should return metrics list`() {
            // Given
            val metrics = listOf(testMetricEntity)
            every { metricService.listMetrics(null, null, null, 50, 0) } returns metrics
            every { metricMapper.toListResponse(testMetricEntity) } returns testMetricListResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/metrics"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("test_catalog.test_schema.test_metric"))
                .andExpect(jsonPath("$[0].owner").value("test@example.com"))

            verify(exactly = 1) { metricService.listMetrics(null, null, null, 50, 0) }
            verify(exactly = 1) { metricMapper.toListResponse(testMetricEntity) }
        }

        @Test
        @DisplayName("should filter metrics by tag")
        fun `should filter metrics by tag`() {
            // Given
            val tag = "test"
            val metrics = listOf(testMetricEntity)
            every { metricService.listMetrics(tag, null, null, 50, 0) } returns metrics
            every { metricMapper.toListResponse(testMetricEntity) } returns testMetricListResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("tag", tag),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { metricService.listMetrics(tag, null, null, 50, 0) }
        }

        @Test
        @DisplayName("should filter metrics by owner")
        fun `should filter metrics by owner`() {
            // Given
            val owner = "test@example.com"
            val metrics = listOf(testMetricEntity)
            every { metricService.listMetrics(null, owner, null, 50, 0) } returns metrics
            every { metricMapper.toListResponse(testMetricEntity) } returns testMetricListResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("owner", owner),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify(exactly = 1) { metricService.listMetrics(null, owner, null, 50, 0) }
        }

        @Test
        @DisplayName("should apply limit and offset")
        fun `should apply limit and offset`() {
            // Given
            val limit = 10
            val offset = 5
            every { metricService.listMetrics(null, null, null, limit, offset) } returns emptyList()

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("limit", limit.toString())
                        .param("offset", offset.toString()),
                ).andExpect(status().isOk)

            verify(exactly = 1) { metricService.listMetrics(null, null, null, limit, offset) }
        }

        @Test
        @DisplayName("should reject request when limit exceeds maximum")
        fun `should reject request when limit exceeds maximum`() {
            // When limit exceeds 500, validation should reject the request
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("limit", "501"),
                ).andExpect(status().is4xxClientError)
        }

        @Test
        @DisplayName("should reject request when offset is negative")
        fun `should reject request when offset is negative`() {
            // When offset is negative, validation should reject the request
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("offset", "-1"),
                ).andExpect(status().is4xxClientError)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/metrics/{name}")
    inner class GetMetric {
        @Test
        @DisplayName("should return metric by name")
        fun `should return metric by name`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            every { metricService.getMetricOrThrow(name) } returns testMetricEntity
            every { metricMapper.toResponse(testMetricEntity) } returns testMetricResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/metrics/$name"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.owner").value("test@example.com"))
                .andExpect(jsonPath("$.sql").value("SELECT COUNT(*) FROM users"))

            verify(exactly = 1) { metricService.getMetricOrThrow(name) }
            verify(exactly = 1) { metricMapper.toResponse(testMetricEntity) }
        }

        @Test
        @DisplayName("should return 404 when metric not found")
        fun `should return 404 when metric not found`() {
            // Given
            val name = "nonexistent_catalog.schema.metric"
            every { metricService.getMetricOrThrow(name) } throws MetricNotFoundException(name)

            // When & Then
            mockMvc
                .perform(get("/api/v1/metrics/$name"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { metricService.getMetricOrThrow(name) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/metrics")
    inner class CreateMetric {
        @Test
        @DisplayName("should create metric successfully")
        fun `should create metric successfully`() {
            // Given
            val request =
                CreateMetricRequest(
                    name = "new_catalog.new_schema.new_metric",
                    owner = "new@example.com",
                    sql = "SELECT SUM(amount) FROM orders",
                    description = "New metric",
                    tags = listOf("new"),
                )

            val params =
                CreateMetricCommand(
                    name = request.name,
                    owner = request.owner,
                    team = null,
                    description = request.description,
                    sql = request.sql,
                    sourceTable = null,
                    tags = request.tags.toSet(),
                )

            every { metricMapper.extractCreateCommand(request) } returns params
            every {
                metricService.createMetric(
                    name = params.name,
                    owner = params.owner,
                    team = params.team,
                    description = params.description,
                    sql = params.sql,
                    sourceTable = params.sourceTable,
                    tags = params.tags.toList(),
                )
            } returns
                MetricEntity(
                    name = request.name,
                    owner = request.owner,
                    sql = request.sql,
                    description = request.description,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(request.name))
                .andExpect(jsonPath("$.message").exists())

            verify(exactly = 1) { metricMapper.extractCreateCommand(request) }
        }

        @Test
        @DisplayName("should return 400 for invalid request - missing name")
        fun `should return 400 for invalid request - missing name`() {
            // Given
            val invalidRequest =
                mapOf(
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for invalid name format")
        fun `should return 400 for invalid name format`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "invalid-name-without-dots",
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for invalid email format")
        fun `should return 400 for invalid email format`() {
            // Given
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.metric",
                    "owner" to "not-an-email",
                    "sql" to "SELECT 1",
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when tags exceed maximum limit")
        fun `should return 400 when tags exceed maximum limit`() {
            // Given
            val tooManyTags = (1..15).map { "tag$it" } // 15 tags, limit is 10
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.metric",
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                    "tags" to tooManyTags,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when SQL exceeds maximum length")
        fun `should return 400 when SQL exceeds maximum length`() {
            // Given
            val tooLongSql = "SELECT " + "x".repeat(10001) // SQL > 10000 chars
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.metric",
                    "owner" to "test@example.com",
                    "sql" to tooLongSql,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when description exceeds maximum length")
        fun `should return 400 when description exceeds maximum length`() {
            // Given
            val tooLongDescription = "x".repeat(1001) // Description > 1000 chars
            val invalidRequest =
                mapOf(
                    "name" to "catalog.schema.metric",
                    "owner" to "test@example.com",
                    "sql" to "SELECT 1",
                    "description" to tooLongDescription,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 409 when metric already exists")
        fun `should return 409 when metric already exists`() {
            // Given
            val request =
                CreateMetricRequest(
                    name = "existing_catalog.schema.metric",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                )

            val params =
                CreateMetricCommand(
                    name = request.name,
                    owner = request.owner,
                    team = null,
                    description = null,
                    sql = request.sql,
                    sourceTable = null,
                    tags = mutableSetOf(),
                )

            every { metricMapper.extractCreateCommand(request) } returns params
            every {
                metricService.createMetric(
                    name = params.name,
                    owner = params.owner,
                    team = any(),
                    description = any(),
                    sql = params.sql,
                    sourceTable = any(),
                    tags = any(),
                )
            } throws MetricAlreadyExistsException(request.name)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/metrics/{name}/run")
    inner class RunMetric {
        @Test
        @DisplayName("should execute metric successfully")
        fun `should execute metric successfully`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val request =
                RunMetricRequest(
                    parameters = mapOf("date" to "2024-01-01"),
                    limit = 100,
                    timeout = 60,
                )

            every {
                metricExecutionService.executeMetric(
                    metricName = name,
                    parameters = request.parameters,
                    limit = request.limit,
                    timeout = request.timeout,
                )
            } returns testExecutionResult

            every { metricMapper.toExecutionResultDto(testExecutionResult) } returns testExecutionResultDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.row_count").value(1))
                .andExpect(jsonPath("$.rendered_sql").value("SELECT COUNT(*) FROM users"))

            verify(exactly = 1) {
                metricExecutionService.executeMetric(
                    metricName = name,
                    parameters = request.parameters,
                    limit = request.limit,
                    timeout = request.timeout,
                )
            }
        }

        @Test
        @DisplayName("should return 404 when running non-existent metric")
        fun `should return 404 when running non-existent metric`() {
            // Given
            val name = "nonexistent_catalog.schema.metric"
            val request = RunMetricRequest()

            every {
                metricExecutionService.executeMetric(
                    metricName = name,
                    parameters = any(),
                    limit = any(),
                    timeout = any(),
                )
            } throws MetricNotFoundException(name)

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        @DisplayName("should return 400 when limit is less than 1")
        fun `should return 400 when limit is less than 1`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "limit" to 0, // Invalid - must be at least 1
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when limit exceeds maximum")
        fun `should return 400 when limit exceeds maximum`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "limit" to 10001, // Invalid - must not exceed 10000
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when timeout is less than 1")
        fun `should return 400 when timeout is less than 1`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "timeout" to 0, // Invalid - must be at least 1
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 when timeout exceeds maximum")
        fun `should return 400 when timeout exceeds maximum`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val invalidRequest =
                mapOf(
                    "parameters" to emptyMap<String, Any>(),
                    "timeout" to 3601, // Invalid - must not exceed 3600
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should execute with default parameters when request body is empty")
        fun `should execute with default parameters when request body is empty`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val request = RunMetricRequest()

            every {
                metricExecutionService.executeMetric(
                    metricName = name,
                    parameters = emptyMap(),
                    limit = null,
                    timeout = 300,
                )
            } returns testExecutionResult

            every { metricMapper.toExecutionResultDto(testExecutionResult) } returns testExecutionResultDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

            verify(exactly = 1) {
                metricExecutionService.executeMetric(
                    metricName = name,
                    parameters = emptyMap(),
                    limit = null,
                    timeout = 300,
                )
            }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        @Test
        @DisplayName("should handle complete create-get-run flow")
        fun `should handle complete create-get-run flow`() {
            // Given - Setup for create
            val createRequest =
                CreateMetricRequest(
                    name = "integration_catalog.schema.metric",
                    owner = "integration@example.com",
                    sql = "SELECT COUNT(*) FROM test_table",
                )

            val createParams =
                CreateMetricCommand(
                    name = createRequest.name,
                    owner = createRequest.owner,
                    team = null,
                    description = null,
                    sql = createRequest.sql,
                    sourceTable = null,
                    tags = mutableSetOf(),
                )

            val createdMetric =
                MetricEntity(
                    name = createRequest.name,
                    owner = createRequest.owner,
                    sql = createRequest.sql,
                )

            val createdMetricResponse =
                MetricResponse(
                    name = createRequest.name,
                    type = "Metric",
                    owner = createRequest.owner,
                    team = null,
                    description = null,
                    tags = emptyList(),
                    sql = createRequest.sql,
                    sourceTable = null,
                    dependencies = emptyList(),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                )

            every { metricMapper.extractCreateCommand(createRequest) } returns createParams
            every {
                metricService.createMetric(
                    name = createParams.name,
                    owner = createParams.owner,
                    team = any(),
                    description = any(),
                    sql = createParams.sql,
                    sourceTable = any(),
                    tags = any(),
                )
            } returns createdMetric

            // When & Then - Create
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value(createRequest.name))

            // Given - Setup for get
            every { metricService.getMetricOrThrow(createRequest.name) } returns createdMetric
            every { metricMapper.toResponse(createdMetric) } returns createdMetricResponse

            // When & Then - Get
            mockMvc
                .perform(get("/api/v1/metrics/${createRequest.name}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value(createRequest.name))

            // Given - Setup for run
            val runRequest = RunMetricRequest()
            every {
                metricExecutionService.executeMetric(
                    metricName = createRequest.name,
                    parameters = any(),
                    limit = any(),
                    timeout = any(),
                )
            } returns testExecutionResult
            every { metricMapper.toExecutionResultDto(testExecutionResult) } returns testExecutionResultDto

            // When & Then - Run
            mockMvc
                .perform(
                    post("/api/v1/metrics/${createRequest.name}/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(runRequest)),
                ).andExpect(status().isOk)

            // Verify all calls
            verify(exactly = 1) { metricMapper.extractCreateCommand(createRequest) }
            verify(exactly = 1) { metricService.getMetricOrThrow(createRequest.name) }
        }
    }
}
