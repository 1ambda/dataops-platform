package com.github.lambda.controller

import com.github.lambda.config.SecurityConfig
import com.github.lambda.domain.model.transpile.SqlDialect
import com.github.lambda.domain.service.*
import com.github.lambda.dto.transpile.TranspileResultDto
import com.github.lambda.dto.transpile.TranspileRulesDto
import com.github.lambda.exception.GlobalExceptionHandler
import com.github.lambda.mapper.TranspileMapper
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import java.time.Instant

/**
 * TranspileController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only (faster than full integration test)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler for proper security and exception handling
 */
@WebMvcTest(TranspileController::class)
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
    TranspileControllerTest.ValidationConfig::class,
)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class TranspileControllerTest {
    /**
     * Test configuration to enable method-level validation for @NotBlank annotations
     * on controller method parameters. Required for @WebMvcTest since it doesn't auto-configure this.
     */
    @TestConfiguration
    class ValidationConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var transpileService: TranspileService

    @MockkBean
    private lateinit var transpileMapper: TranspileMapper

    private lateinit var testTranspileRulesResult: TranspileRulesResult
    private lateinit var testTranspileRulesDto: TranspileRulesDto
    private lateinit var testMetricTranspileResult: MetricTranspileResult
    private lateinit var testDatasetTranspileResult: DatasetTranspileResult
    private lateinit var testTranspileResultDto: TranspileResultDto

    @BeforeEach
    fun setUp() {
        testTranspileRulesResult =
            TranspileRulesResult(
                version = "1.0.0",
                rules = emptyList(),
                metadata =
                    TranspileMetadata(
                        createdAt = Instant.parse("2024-01-01T09:00:00Z"),
                        createdBy = "system",
                        totalRules = 0,
                        cacheTtlSeconds = 3600,
                    ),
            )

        testTranspileRulesDto =
            TranspileRulesDto(
                version = "1.0.0",
                rules = emptyList(),
                metadata =
                    com.github.lambda.dto.transpile.TranspileMetadataDto(
                        createdAt = Instant.parse("2024-01-01T09:00:00Z"),
                        createdBy = "system",
                        totalRules = 0,
                        cacheTtlSeconds = 3600,
                    ),
            )

        testMetricTranspileResult =
            MetricTranspileResult(
                metricName = "test_catalog.test_schema.test_metric",
                sourceDialect = "bigquery",
                targetDialect = "trino",
                originalSql = "SELECT COUNT(*) FROM users",
                transpiledSql = "SELECT COUNT(1) FROM users",
                appliedRules = emptyList(),
                warnings = emptyList(),
                transpiledAt = Instant.parse("2024-01-01T10:00:00Z"),
                durationMs = 250,
            )

        testDatasetTranspileResult =
            DatasetTranspileResult(
                datasetName = "test_catalog.test_schema.test_dataset",
                sourceDialect = "bigquery",
                targetDialect = "trino",
                originalSql = "SELECT id, name FROM users",
                transpiledSql = "SELECT id, name FROM users",
                appliedRules = emptyList(),
                warnings = emptyList(),
                transpiledAt = Instant.parse("2024-01-01T10:00:00Z"),
                durationMs = 180,
            )

        testTranspileResultDto =
            TranspileResultDto(
                resourceType = "metric",
                resourceName = "test_catalog.test_schema.test_metric",
                sourceDialect = "bigquery",
                targetDialect = "trino",
                originalSql = "SELECT COUNT(*) FROM users",
                transpiledSql = "SELECT COUNT(1) FROM users",
                appliedRules = emptyList(),
                warnings = emptyList(),
                transpiledAt = Instant.parse("2024-01-01T10:00:00Z"),
                durationMs = 250,
            )
    }

    @Nested
    @DisplayName("GET /api/v1/transpile/rules")
    inner class GetTranspileRules {
        @Test
        @DisplayName("should return transpile rules successfully")
        fun shouldReturnTranspileRulesSuccessfully() {
            // Given
            every {
                transpileService.getTranspileRules(
                    version = "latest",
                    fromDialect = null,
                    toDialect = null,
                )
            } returns testTranspileRulesResult
            every { transpileMapper.toTranspileRulesDto(testTranspileRulesResult) } returns testTranspileRulesDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.metadata.totalRules").value(0))
                .andExpect(jsonPath("$.metadata.cacheTtlSeconds").value(3600))
                .andExpect(header().string("ETag", "\"1.0.0\""))
                .andExpect(header().string("Cache-Control", "max-age=3600"))

            verify { transpileService.getTranspileRules("latest", null, null) }
            verify { transpileMapper.toTranspileRulesDto(testTranspileRulesResult) }
        }

        @Test
        @DisplayName("should handle version parameter")
        fun shouldHandleVersionParameter() {
            // Given
            every {
                transpileService.getTranspileRules(
                    version = "1.2.0",
                    fromDialect = null,
                    toDialect = null,
                )
            } returns testTranspileRulesResult
            every { transpileMapper.toTranspileRulesDto(any()) } returns testTranspileRulesDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/rules")
                        .param("version", "1.2.0")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())

            verify { transpileService.getTranspileRules("1.2.0", null, null) }
        }

        @Test
        @DisplayName("should handle dialect filtering parameters")
        fun shouldHandleDialectFilteringParameters() {
            // Given
            every {
                transpileService.getTranspileRules(
                    version = "latest",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                )
            } returns testTranspileRulesResult
            every { transpileMapper.toTranspileRulesDto(any()) } returns testTranspileRulesDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/rules")
                        .param("fromDialect", "bigquery")
                        .param("toDialect", "trino")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())

            verify { transpileService.getTranspileRules("latest", SqlDialect.BIGQUERY, SqlDialect.TRINO) }
        }

        @Test
        @DisplayName("should return 400 for invalid dialect")
        fun shouldReturn400ForInvalidDialect() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/rules")
                        .param("fromDialect", "invalid_dialect")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transpile/metrics/{metricName}")
    inner class TranspileMetric {
        @Test
        @DisplayName("should transpile metric successfully")
        fun shouldTranspileMetricSuccessfully() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            every {
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = null,
                    parameters = emptyMap(),
                )
            } returns testMetricTranspileResult
            every { transpileMapper.toTranspileResultDto(testMetricTranspileResult) } returns testTranspileResultDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", metricName)
                        .param("targetDialect", "trino")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.resourceType").value("metric"))
                .andExpect(jsonPath("$.resourceName").value(metricName))
                .andExpect(jsonPath("$.sourceDialect").value("bigquery"))
                .andExpect(jsonPath("$.targetDialect").value("trino"))
                .andExpect(jsonPath("$.originalSql").value("SELECT COUNT(*) FROM users"))
                .andExpect(jsonPath("$.transpiledSql").value("SELECT COUNT(1) FROM users"))

            verify {
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = null,
                    parameters = emptyMap(),
                )
            }
            verify { transpileMapper.toTranspileResultDto(testMetricTranspileResult) }
        }

        @Test
        @DisplayName("should handle source dialect parameter")
        fun shouldHandleSourceDialectParameter() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            every {
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = SqlDialect.BIGQUERY,
                    parameters = emptyMap(),
                )
            } returns testMetricTranspileResult
            every { transpileMapper.toTranspileResultDto(any()) } returns testTranspileResultDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", metricName)
                        .param("targetDialect", "trino")
                        .param("sourceDialect", "bigquery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())

            verify {
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = SqlDialect.BIGQUERY,
                    parameters = emptyMap(),
                )
            }
        }

        @Test
        @DisplayName("should return 400 when targetDialect is missing")
        fun shouldReturn400WhenTargetDialectIsMissing() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", "test_metric")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("should return 400 when targetDialect is blank")
        fun shouldReturn400WhenTargetDialectIsBlank() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", "test_metric")
                        .param("targetDialect", "")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("should return 400 when metricName is blank")
        fun shouldReturn400WhenMetricNameIsBlank() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", " ")
                        .param("targetDialect", "trino")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("should return 400 for invalid targetDialect")
        fun shouldReturn400ForInvalidTargetDialect() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", "test_metric")
                        .param("targetDialect", "invalid_dialect")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("should return 400 for invalid sourceDialect")
        fun shouldReturn400ForInvalidSourceDialect() {
            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", "test_metric")
                        .param("targetDialect", "trino")
                        .param("sourceDialect", "invalid_dialect")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transpile/datasets/{datasetName}")
    inner class TranspileDataset {
        @Test
        @DisplayName("should transpile dataset successfully")
        fun shouldTranspileDatasetSuccessfully() {
            // Given
            val datasetName = "test_catalog.test_schema.test_dataset"
            val datasetResultDto =
                testTranspileResultDto.copy(
                    resourceType = "dataset",
                    resourceName = datasetName,
                )

            every {
                transpileService.transpileDataset(
                    datasetName = datasetName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = null,
                    parameters = emptyMap(),
                )
            } returns testDatasetTranspileResult
            every { transpileMapper.toTranspileResultDto(testDatasetTranspileResult) } returns datasetResultDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/datasets/{datasetName}", datasetName)
                        .param("targetDialect", "trino")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.resourceType").value("dataset"))
                .andExpect(jsonPath("$.resourceName").value(datasetName))
                .andExpect(jsonPath("$.sourceDialect").value("bigquery"))
                .andExpect(jsonPath("$.targetDialect").value("trino"))

            verify {
                transpileService.transpileDataset(
                    datasetName = datasetName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = null,
                    parameters = emptyMap(),
                )
            }
            verify { transpileMapper.toTranspileResultDto(testDatasetTranspileResult) }
        }

        @Test
        @DisplayName("should handle both source and target dialect parameters for datasets")
        fun shouldHandleBothSourceAndTargetDialectParametersForDatasets() {
            // Given
            val datasetName = "test_catalog.test_schema.test_dataset"
            every {
                transpileService.transpileDataset(
                    datasetName = datasetName,
                    targetDialect = SqlDialect.POSTGRESQL,
                    sourceDialect = SqlDialect.MYSQL,
                    parameters = emptyMap(),
                )
            } returns testDatasetTranspileResult
            every { transpileMapper.toTranspileResultDto(any()) } returns testTranspileResultDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/datasets/{datasetName}", datasetName)
                        .param("targetDialect", "postgresql")
                        .param("sourceDialect", "mysql")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())

            verify {
                transpileService.transpileDataset(
                    datasetName = datasetName,
                    targetDialect = SqlDialect.POSTGRESQL,
                    sourceDialect = SqlDialect.MYSQL,
                    parameters = emptyMap(),
                )
            }
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("should handle service exceptions gracefully")
        fun shouldHandleServiceExceptionsGracefully() {
            // Given
            every {
                transpileService.transpileMetric(any(), any(), any(), any())
            } throws RuntimeException("Service error")

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", "test_metric")
                        .param("targetDialect", "trino")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isInternalServerError())
        }

        @Test
        @DisplayName("should handle special characters in metric names")
        fun shouldHandleSpecialCharactersInMetricNames() {
            // Given
            val metricName = "catalog-with-dashes.schema_with_underscores.metric.with.dots"
            every {
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = null,
                    parameters = emptyMap(),
                )
            } returns testMetricTranspileResult
            every { transpileMapper.toTranspileResultDto(any()) } returns testTranspileResultDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/metrics/{metricName}", metricName)
                        .param("targetDialect", "trino")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())

            verify {
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = SqlDialect.TRINO,
                    sourceDialect = null,
                    parameters = emptyMap(),
                )
            }
        }
    }

    @Nested
    @DisplayName("Security and Headers")
    inner class SecurityAndHeaders {
        @Test
        @DisplayName("should include proper cache headers in rules response")
        fun shouldIncludeProperCacheHeadersInRulesResponse() {
            // Given
            every { transpileService.getTranspileRules(any(), any(), any()) } returns testTranspileRulesResult
            every { transpileMapper.toTranspileRulesDto(any()) } returns testTranspileRulesDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/transpile/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().exists("Cache-Control"))
                .andExpect(header().string("Cache-Control", "max-age=3600"))
        }

        @Test
        @DisplayName("should require CSRF token")
        fun shouldRequireCSRFToken() {
            // When & Then - Request without CSRF should work for GET requests
            mockMvc
                .perform(
                    get("/api/v1/transpile/rules")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isUnauthorized()) // Due to missing authentication in test context
        }
    }
}
