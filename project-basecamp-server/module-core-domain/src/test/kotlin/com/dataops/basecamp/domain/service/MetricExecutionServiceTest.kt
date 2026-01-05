package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.MetricNotFoundException
import com.dataops.basecamp.domain.entity.metric.MetricEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * MetricExecutionService Unit Tests
 *
 * Tests metric SQL execution and rendering logic.
 */
@DisplayName("MetricExecutionService Unit Tests")
class MetricExecutionServiceTest {
    private val metricService: MetricService = mockk()
    private val metricExecutionService = MetricExecutionService(metricService)

    private lateinit var testMetric: MetricEntity

    @BeforeEach
    fun setUp() {
        testMetric =
            MetricEntity(
                name = "test_catalog.test_schema.test_metric",
                owner = "test@example.com",
                sql = "SELECT COUNT(*) as count FROM users WHERE created_at > '{{start_date}}'",
            )
    }

    @Nested
    @DisplayName("executeMetric")
    inner class ExecuteMetric {
        @Test
        @DisplayName("should execute metric and return results")
        fun `should execute metric and return results`() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            val parameters = mapOf("start_date" to "2024-01-01")

            every { metricService.getMetricOrThrow(metricName) } returns testMetric

            // When
            val result =
                metricExecutionService.executeMetric(
                    metricName = metricName,
                    parameters = parameters,
                )

            // Then
            assertThat(result.rows).isNotEmpty()
            assertThat(result.rowCount).isGreaterThan(0)
            assertThat(result.durationSeconds).isGreaterThan(0.0)
            assertThat(result.renderedSql).contains("'2024-01-01'")
            verify(exactly = 1) { metricService.getMetricOrThrow(metricName) }
        }

        @Test
        @DisplayName("should throw MetricNotFoundException when metric does not exist")
        fun `should throw MetricNotFoundException when metric does not exist`() {
            // Given
            val metricName = "nonexistent_catalog.schema.metric"
            every { metricService.getMetricOrThrow(metricName) } throws MetricNotFoundException(metricName)

            // When & Then
            val exception =
                assertThrows<MetricNotFoundException> {
                    metricExecutionService.executeMetric(metricName = metricName)
                }

            assertThat(exception.message).contains(metricName)
        }

        @Test
        @DisplayName("should apply limit to results")
        fun `should apply limit to results`() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            val limit = 5

            every { metricService.getMetricOrThrow(metricName) } returns testMetric

            // When
            val result =
                metricExecutionService.executeMetric(
                    metricName = metricName,
                    limit = limit,
                )

            // Then
            assertThat(result.rows.size).isLessThanOrEqualTo(limit)
            assertThat(result.rowCount).isLessThanOrEqualTo(limit)
        }

        @Test
        @DisplayName("should render SQL with string parameters")
        fun `should render SQL with string parameters`() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            val parameters = mapOf("start_date" to "2024-01-01")

            every { metricService.getMetricOrThrow(metricName) } returns testMetric

            // When
            val result =
                metricExecutionService.executeMetric(
                    metricName = metricName,
                    parameters = parameters,
                )

            // Then
            assertThat(result.renderedSql).contains("'2024-01-01'")
            assertThat(result.renderedSql).doesNotContain("{{start_date}}")
        }

        @Test
        @DisplayName("should render SQL with numeric parameters")
        fun `should render SQL with numeric parameters`() {
            // Given
            val metricWithNumbers =
                testMetric.apply {
                    sql = "SELECT * FROM orders WHERE amount > {{min_amount}}"
                }
            val metricName = "test_catalog.test_schema.test_metric"
            val parameters = mapOf("min_amount" to 100)

            every { metricService.getMetricOrThrow(metricName) } returns metricWithNumbers

            // When
            val result =
                metricExecutionService.executeMetric(
                    metricName = metricName,
                    parameters = parameters,
                )

            // Then
            assertThat(result.renderedSql).contains("100")
            assertThat(result.renderedSql).doesNotContain("{{min_amount}}")
        }

        @Test
        @DisplayName("should render SQL with boolean parameters")
        fun `should render SQL with boolean parameters`() {
            // Given
            val metricWithBoolean =
                testMetric.apply {
                    sql = "SELECT * FROM users WHERE is_active = {{is_active}}"
                }
            val metricName = "test_catalog.test_schema.test_metric"
            val parameters = mapOf("is_active" to true)

            every { metricService.getMetricOrThrow(metricName) } returns metricWithBoolean

            // When
            val result =
                metricExecutionService.executeMetric(
                    metricName = metricName,
                    parameters = parameters,
                )

            // Then
            assertThat(result.renderedSql).contains("true")
            assertThat(result.renderedSql).doesNotContain("{{is_active}}")
        }
    }

    @Nested
    @DisplayName("dryRun")
    inner class DryRun {
        @Test
        @DisplayName("should render SQL without executing")
        fun `should render SQL without executing`() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            val parameters = mapOf("start_date" to "2024-06-01")

            every { metricService.getMetricOrThrow(metricName) } returns testMetric

            // When
            val result =
                metricExecutionService.dryRun(
                    metricName = metricName,
                    parameters = parameters,
                )

            // Then
            assertThat(result).contains("'2024-06-01'")
            assertThat(result).doesNotContain("{{start_date}}")
        }

        @Test
        @DisplayName("should throw MetricNotFoundException for non-existent metric")
        fun `should throw MetricNotFoundException for non-existent metric`() {
            // Given
            val metricName = "nonexistent_catalog.schema.metric"
            every { metricService.getMetricOrThrow(metricName) } throws MetricNotFoundException(metricName)

            // When & Then
            assertThrows<MetricNotFoundException> {
                metricExecutionService.dryRun(metricName = metricName)
            }
        }

        @Test
        @DisplayName("should preserve SQL when no parameters provided")
        fun `should preserve SQL when no parameters provided`() {
            // Given
            val metricWithNoParams =
                testMetric.apply {
                    sql = "SELECT COUNT(*) FROM users"
                }
            val metricName = "test_catalog.test_schema.test_metric"

            every { metricService.getMetricOrThrow(metricName) } returns metricWithNoParams

            // When
            val result = metricExecutionService.dryRun(metricName = metricName)

            // Then
            assertThat(result).isEqualTo("SELECT COUNT(*) FROM users")
        }

        @Test
        @DisplayName("should keep unreplaced placeholders when parameters not provided")
        fun `should keep unreplaced placeholders when parameters not provided`() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            every { metricService.getMetricOrThrow(metricName) } returns testMetric

            // When - no parameters provided but SQL has {{start_date}}
            val result = metricExecutionService.dryRun(metricName = metricName, parameters = emptyMap())

            // Then - placeholder remains unreplaced
            assertThat(result).contains("{{start_date}}")
        }

        @Test
        @DisplayName("should handle multiple parameters in SQL")
        fun `should handle multiple parameters in SQL`() {
            // Given
            val metricWithMultipleParams =
                testMetric.apply {
                    sql =
                        "SELECT * FROM users WHERE created_at > '{{start_date}}' AND status = '{{status}}' AND age > {{min_age}}"
                }
            val metricName = "test_catalog.test_schema.test_metric"
            val parameters =
                mapOf(
                    "start_date" to "2024-01-01",
                    "status" to "active",
                    "min_age" to 18,
                )

            every { metricService.getMetricOrThrow(metricName) } returns metricWithMultipleParams

            // When
            val result = metricExecutionService.dryRun(metricName = metricName, parameters = parameters)

            // Then
            assertThat(result).contains("'2024-01-01'")
            assertThat(result).contains("'active'")
            assertThat(result).contains("18")
            assertThat(result).doesNotContain("{{start_date}}")
            assertThat(result).doesNotContain("{{status}}")
            assertThat(result).doesNotContain("{{min_age}}")
        }
    }
}
