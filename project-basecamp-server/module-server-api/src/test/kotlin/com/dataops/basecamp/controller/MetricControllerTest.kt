package com.dataops.basecamp.controller

import com.dataops.basecamp.common.exception.MetricAlreadyExistsException
import com.dataops.basecamp.common.exception.MetricNotFoundException
import com.dataops.basecamp.domain.command.metric.CreateMetricCommand
import com.dataops.basecamp.domain.entity.metric.MetricEntity
import com.dataops.basecamp.domain.projection.metric.MetricExecutionProjection
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
                owner = "test@example.com",
                team = "data-team",
                description = "Test metric description",
                sql = "SELECT COUNT(*) FROM users",
                sourceTable = "users",
                tags = listOf("test", "user"),
                dependencies = listOf("users"),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        testMetricListResponse =
            MetricResponse(
                name = "test_catalog.test_schema.another_metric",
                owner = "test@example.com",
                team = "data-team",
                description = "Another metric",
                sql = "SELECT AVG(price) FROM products",
                sourceTable = "products",
                tags = listOf("test"),
                dependencies = listOf("products"),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        testExecutionResult =
            MetricExecutionProjection(
                rows = emptyList(),
                rowCount = 1,
                durationSeconds = 0.15,
                renderedSql = "SELECT COUNT(*) FROM users",
            )

        testExecutionResultDto =
            MetricExecutionResultDto(
                rows = emptyList(),
                rowCount = 1,
                durationSeconds = 0.15,
                renderedSql = "SELECT COUNT(*) FROM users",
            )
    }

    @Nested
    @DisplayName("GET /api/v1/metrics - List metrics")
    inner class ListMetrics {
        @Test
        fun `should return 200 with metrics list when found`() {
            // Given
            val metrics = listOf(testMetricEntity)
            every { metricService.listMetrics(any(), any(), any(), any(), any()) } returns metrics
            every { metricMapper.toListResponse(testMetricEntity) } returns testMetricResponse

            // When & Then
            mockMvc
                .perform(get("/api/v1/metrics"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("test_catalog.test_schema.test_metric"))

            verify(exactly = 1) { metricService.listMetrics(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should return 200 with empty list when no metrics found`() {
            // Given
            every { metricService.listMetrics(any(), any(), any(), any(), any()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/metrics"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isEmpty)

            verify(exactly = 1) { metricService.listMetrics(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `should return 200 with custom limit and offset`() {
            // Given
            val metrics = listOf(testMetricEntity)
            every { metricService.listMetrics(any(), any(), any(), any(), any()) } returns metrics
            every { metricMapper.toListResponse(testMetricEntity) } returns testMetricResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("limit", "50")
                        .param("offset", "10"),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))

            verify(exactly = 1) { metricService.listMetrics(limit = 50, offset = 10) }
        }

        @Test
        fun `should return 400 when limit exceeds maximum`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("limit", "1001"), // Max is 1000
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when limit is negative`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("limit", "-1"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when offset is negative`() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/metrics")
                        .param("offset", "-1"),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/metrics/:name - Get metric by name")
    inner class GetMetric {
        @Test
        fun `should return 200 with metric when found`() {
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

            verify(exactly = 1) { metricService.getMetricOrThrow(name) }
        }

        @Test
        fun `should return 404 when metric not found`() {
            // Given
            val name = "nonexistent.metric"
            every { metricService.getMetricOrThrow(name) } throws MetricNotFoundException("Metric not found: $name")

            // When & Then
            mockMvc
                .perform(get("/api/v1/metrics/$name"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { metricService.getMetricOrThrow(name) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/metrics - Create metric")
    inner class CreateMetric {
        @Test
        fun `should return 201 when metric created successfully`() {
            // Given
            val request =
                CreateMetricRequest(
                    name = "test_catalog.test_schema.test_metric",
                    owner = "test@example.com",
                    team = "data-team",
                    description = "Test metric description",
                    sql = "SELECT COUNT(*) FROM users",
                    sourceTable = "users",
                    tags = listOf("test", "user"),
                )

            val command =
                CreateMetricCommand(
                    name = request.name,
                    owner = request.owner,
                    team = request.team,
                    description = request.description,
                    sql = request.sql,
                    sourceTable = request.sourceTable,
                    tags = request.tags.toSet(),
                )

            every { metricMapper.extractCreateCommand(request) } returns command
            every {
                metricService.createMetric(
                    name = command.name,
                    owner = command.owner,
                    team = command.team,
                    description = command.description,
                    sql = command.sql,
                    sourceTable = command.sourceTable,
                    tags = command.tags.toList(),
                )
            } returns testMetricEntity

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.name").value(request.name))

            verify(exactly = 1) {
                metricService.createMetric(
                    name = command.name,
                    owner = command.owner,
                    team = command.team,
                    description = command.description,
                    sql = command.sql,
                    sourceTable = command.sourceTable,
                    tags = command.tags.toList(),
                )
            }
        }

        @Test
        fun `should return 409 when metric already exists`() {
            // Given
            val request =
                CreateMetricRequest(
                    name = "test_catalog.test_schema.existing_metric",
                    owner = "test@example.com",
                    team = "data-team",
                    description = "Test",
                    sql = "SELECT 1",
                    sourceTable = "table",
                    tags = emptyList(),
                )

            val command =
                CreateMetricCommand(
                    name = request.name,
                    owner = request.owner,
                    team = request.team,
                    description = request.description,
                    sql = request.sql,
                    sourceTable = request.sourceTable,
                    tags = request.tags.toSet(),
                )

            every { metricMapper.extractCreateCommand(request) } returns command
            every {
                metricService.createMetric(
                    name = command.name,
                    owner = command.owner,
                    team = command.team,
                    description = command.description,
                    sql = command.sql,
                    sourceTable = command.sourceTable,
                    tags = command.tags.toList(),
                )
            } throws MetricAlreadyExistsException("Metric already exists: ${request.name}")

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)

            verify(exactly = 1) {
                metricService.createMetric(
                    name = command.name,
                    owner = command.owner,
                    team = command.team,
                    description = command.description,
                    sql = command.sql,
                    sourceTable = command.sourceTable,
                    tags = command.tags.toList(),
                )
            }
        }

        @Test
        fun `should return 400 when name is blank`() {
            // Given
            val request =
                CreateMetricRequest(
                    name = "",
                    owner = "test@example.com",
                    team = "data-team",
                    description = "Test",
                    sql = "SELECT 1",
                    sourceTable = "table",
                    tags = emptyList(),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when name is too long`() {
            // Given
            val longName = "a".repeat(256)
            val request =
                CreateMetricRequest(
                    name = longName,
                    owner = "test@example.com",
                    team = "data-team",
                    description = "Test",
                    sql = "SELECT 1",
                    sourceTable = "table",
                    tags = emptyList(),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when SQL is blank`() {
            // Given
            val request =
                CreateMetricRequest(
                    name = "test.metric",
                    owner = "test@example.com",
                    team = "data-team",
                    description = "Test",
                    sql = "",
                    sourceTable = "table",
                    tags = emptyList(),
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/metrics/:name/run - Run metric")
    inner class RunMetric {
        @Test
        fun `should return 200 with execution result when metric runs successfully`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val request =
                RunMetricRequest(
                    parameters = mapOf("start_date" to "2024-01-01"),
                    limit = 100,
                    timeout = 30,
                )

            every {
                metricService.executeMetric(
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
                metricService.executeMetric(
                    metricName = name,
                    parameters = request.parameters,
                    limit = request.limit,
                    timeout = request.timeout,
                )
            }
        }

        @Test
        fun `should return 404 when metric not found`() {
            // Given
            val name = "nonexistent.metric"
            val request =
                RunMetricRequest(
                    parameters = emptyMap(),
                    limit = 100,
                    timeout = 30,
                )

            every {
                metricService.executeMetric(
                    metricName = name,
                    parameters = request.parameters,
                    limit = request.limit,
                    timeout = request.timeout,
                )
            } throws MetricNotFoundException("Metric not found: $name")

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)

            verify(exactly = 1) {
                metricService.executeMetric(
                    metricName = name,
                    parameters = request.parameters,
                    limit = request.limit,
                    timeout = request.timeout,
                )
            }
        }

        @Test
        fun `should return 400 when limit exceeds maximum`() {
            // Given
            val name = "test.metric"
            val request =
                RunMetricRequest(
                    parameters = emptyMap(),
                    limit = 10001, // Max is 10000
                    timeout = 30,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when limit is negative`() {
            // Given
            val name = "test.metric"
            val request =
                RunMetricRequest(
                    parameters = emptyMap(),
                    limit = -1,
                    timeout = 30,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when timeout exceeds maximum`() {
            // Given
            val name = "test.metric"
            val request =
                RunMetricRequest(
                    parameters = emptyMap(),
                    limit = 100,
                    timeout = 3601, // Max is 3600
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when timeout is negative`() {
            // Given
            val name = "test.metric"
            val request =
                RunMetricRequest(
                    parameters = emptyMap(),
                    limit = 100,
                    timeout = -1,
                )

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/metrics/$name/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {
        @Test
        fun `should handle complete workflow - create and run metric`() {
            // Given - Create metric
            val createRequest =
                CreateMetricRequest(
                    name = "test_catalog.test_schema.workflow_metric",
                    owner = "test@example.com",
                    team = "data-team",
                    description = "Integration test metric",
                    sql = "SELECT COUNT(*) FROM users WHERE created_at > '{{ start_date }}'",
                    sourceTable = "users",
                    tags = listOf("integration", "test"),
                )

            val createCommand =
                CreateMetricCommand(
                    name = createRequest.name,
                    owner = createRequest.owner,
                    team = createRequest.team,
                    description = createRequest.description,
                    sql = createRequest.sql,
                    sourceTable = createRequest.sourceTable,
                    tags = createRequest.tags.toSet(),
                )

            every { metricMapper.extractCreateCommand(createRequest) } returns createCommand
            every {
                metricService.createMetric(
                    name = createCommand.name,
                    owner = createCommand.owner,
                    team = createCommand.team,
                    description = createCommand.description,
                    sql = createCommand.sql,
                    sourceTable = createCommand.sourceTable,
                    tags = createCommand.tags.toList(),
                )
            } returns testMetricEntity

            // When - Create metric
            mockMvc
                .perform(
                    post("/api/v1/metrics")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)),
                ).andExpect(status().isCreated)

            // Given - Run metric
            val runRequest =
                RunMetricRequest(
                    parameters = mapOf("start_date" to "2024-01-01"),
                    limit = 100,
                    timeout = 30,
                )

            every {
                metricService.executeMetric(
                    metricName = createRequest.name,
                    parameters = runRequest.parameters,
                    limit = runRequest.limit,
                    timeout = runRequest.timeout,
                )
            } returns testExecutionResult

            every { metricMapper.toExecutionResultDto(testExecutionResult) } returns testExecutionResultDto

            // When - Run metric
            mockMvc
                .perform(
                    post("/api/v1/metrics/${createRequest.name}/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(runRequest)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.row_count").value(1))

            verify(exactly = 1) {
                metricService.createMetric(
                    name = createCommand.name,
                    owner = createCommand.owner,
                    team = createCommand.team,
                    description = createCommand.description,
                    sql = createCommand.sql,
                    sourceTable = createCommand.sourceTable,
                    tags = createCommand.tags.toList(),
                )
            }
            verify(exactly = 1) {
                metricService.executeMetric(
                    metricName = createRequest.name,
                    parameters = runRequest.parameters,
                    limit = runRequest.limit,
                    timeout = runRequest.timeout,
                )
            }
        }
    }
}
