package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.*
import com.dataops.basecamp.domain.entity.dataset.DatasetEntity
import com.dataops.basecamp.domain.entity.metric.MetricEntity
import com.dataops.basecamp.domain.entity.transpile.TranspileRuleEntity
import com.dataops.basecamp.domain.external.parser.AppliedTransformationResponse
import com.dataops.basecamp.domain.external.parser.BasecampParserClient
import com.dataops.basecamp.domain.external.parser.TranspileResponse
import com.dataops.basecamp.domain.external.parser.TranspileRuleRequest
import com.dataops.basecamp.domain.external.parser.TranspileWarningResponse
import com.dataops.basecamp.domain.repository.transpile.TranspileRuleRepositoryDsl
import com.dataops.basecamp.domain.repository.transpile.TranspileRuleRepositoryJpa
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
 * TranspileService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 * Focuses on SQL dialect detection, rule application, and error handling.
 */
@DisplayName("TranspileService")
class TranspileServiceTest {
    private val transpileRuleRepositoryJpa: TranspileRuleRepositoryJpa = mockk()
    private val transpileRuleRepositoryDsl: TranspileRuleRepositoryDsl = mockk()
    private val metricService: MetricService = mockk()
    private val datasetService: DatasetService = mockk()
    private val basecampParserClient: BasecampParserClient = mockk()

    private val transpileService =
        TranspileService(
            transpileRuleRepositoryJpa = transpileRuleRepositoryJpa,
            transpileRuleRepositoryDsl = transpileRuleRepositoryDsl,
            metricService = metricService,
            datasetService = datasetService,
            basecampParserClient = basecampParserClient,
        )

    private lateinit var testMetric: MetricEntity
    private lateinit var testDataset: DatasetEntity
    private lateinit var testRule: TranspileRuleEntity

    @BeforeEach
    fun setUp() {
        testMetric =
            MetricEntity(
                name = "test_catalog.test_schema.test_metric",
                owner = "test@example.com",
                team = "data-team",
                description = "Test metric description",
                sql = "SELECT COUNT(*) FROM users WHERE created_at >= '{{date}}'",
                tags = mutableSetOf("test", "user"),
                dependencies = mutableSetOf("users"),
            )

        testDataset =
            DatasetEntity(
                name = "test_catalog.test_schema.test_dataset",
                owner = "test@example.com",
                team = "data-team",
                description = "Test dataset description",
                sql = "SELECT id, name, created_at FROM users WHERE created_at >= '{{date}}'",
                tags = mutableSetOf("test", "user"),
                dependencies = mutableSetOf("users"),
            )

        testRule =
            TranspileRuleEntity(
                name = "bigquery_to_trino_datetime",
                fromDialect = SqlDialect.BIGQUERY,
                toDialect = SqlDialect.TRINO,
                pattern = "DATETIME\\((.+?)\\)",
                replacement = "CAST(\\$1 AS TIMESTAMP)",
                priority = 100,
                enabled = true,
                description = "Convert BigQuery DATETIME to Trino TIMESTAMP",
            )
    }

    @Nested
    @DisplayName("getTranspileRules")
    inner class GetTranspileRules {
        @Test
        @DisplayName("should return all transpile rules successfully")
        fun shouldReturnAllTranspileRules() {
            // Given
            val rules = listOf(testRule)
            every {
                transpileRuleRepositoryDsl.findByDialectsAndEnabled(
                    fromDialect = null,
                    toDialect = null,
                    enabled = true,
                )
            } returns rules

            // When
            val result = transpileService.getTranspileRules()

            // Then
            assertThat(result.rules).hasSize(1)
            assertThat(result.rules.first().name).isEqualTo(testRule.name)
            assertThat(result.rules.first().fromDialect).isEqualTo(testRule.fromDialect)
            assertThat(result.rules.first().toDialect).isEqualTo(testRule.toDialect)
            assertThat(result.metadata.totalRules).isEqualTo(1)

            verify(exactly = 1) {
                transpileRuleRepositoryDsl.findByDialectsAndEnabled(
                    fromDialect = null,
                    toDialect = null,
                    enabled = true,
                )
            }
        }

        @Test
        @DisplayName("should return empty list when no rules exist")
        fun shouldReturnEmptyListWhenNoRulesExist() {
            // Given
            every {
                transpileRuleRepositoryDsl.findByDialectsAndEnabled(
                    fromDialect = null,
                    toDialect = null,
                    enabled = true,
                )
            } returns emptyList()

            // When
            val result = transpileService.getTranspileRules()

            // Then
            assertThat(result.rules).isEmpty()
            assertThat(result.metadata.totalRules).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("transpileMetric")
    inner class TranspileMetric {
        @Test
        @DisplayName("should transpile metric SQL successfully")
        fun shouldTranspileMetricSQLSuccessfully() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            val targetDialect = SqlDialect.TRINO
            val sourceDialect = SqlDialect.BIGQUERY
            val parameters = mapOf("date" to "2024-01-01")

            val rules = listOf(testRule)
            val parserRules =
                listOf(
                    TranspileRuleRequest(
                        name = testRule.name,
                        pattern = testRule.pattern,
                        replacement = testRule.replacement,
                    ),
                )

            val parserResult =
                TranspileResponse(
                    success = true,
                    transpiledSql = "SELECT COUNT(*) FROM users WHERE created_at >= CAST('2024-01-01' AS TIMESTAMP)",
                    appliedTransformations =
                        listOf(
                            AppliedTransformationResponse(
                                name = "bigquery_to_trino_datetime",
                                type = "datetime_conversion",
                                from = "created_at >= '{{date}}'",
                                to = "created_at >= CAST('2024-01-01' AS TIMESTAMP)",
                            ),
                        ),
                    warnings =
                        listOf(
                            TranspileWarningResponse(
                                type = "PARAMETER_SUBSTITUTION",
                                message = "Parameter '{{date}}' was substituted",
                                line = 1,
                                column = 45,
                            ),
                        ),
                    errorMessage = null,
                )

            every { metricService.getMetric(metricName) } returns testMetric
            every {
                transpileRuleRepositoryDsl.findApplicableRules(
                    fromDialect = sourceDialect,
                    toDialect = targetDialect,
                    orderByPriority = true,
                )
            } returns rules
            every {
                basecampParserClient.transpileSQL(
                    sql = testMetric.sql,
                    sourceDialect = sourceDialect.name.lowercase(),
                    targetDialect = targetDialect.name.lowercase(),
                    rules = parserRules,
                )
            } returns parserResult

            // When
            val result =
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = targetDialect,
                    sourceDialect = sourceDialect,
                    parameters = parameters,
                )

            // Then
            assertThat(result.metricName).isEqualTo(metricName)
            assertThat(result.sourceDialect).isEqualTo(sourceDialect.name.lowercase())
            assertThat(result.targetDialect).isEqualTo(targetDialect.name.lowercase())
            assertThat(result.originalSql).isEqualTo(testMetric.sql)
            assertThat(result.transpiledSql).isEqualTo(parserResult.transpiledSql)
            assertThat(result.appliedRules).hasSize(1)
            assertThat(result.appliedRules.first().name).isEqualTo("bigquery_to_trino_datetime")
            assertThat(result.warnings).hasSize(1)
            assertThat(result.warnings.first().type).isEqualTo("PARAMETER_SUBSTITUTION")
            assertThat(result.durationMs).isGreaterThanOrEqualTo(0)

            verify(exactly = 1) { metricService.getMetric(metricName) }
            verify(exactly = 1) { transpileRuleRepositoryDsl.findApplicableRules(sourceDialect, targetDialect, true) }
            verify(exactly = 1) { basecampParserClient.transpileSQL(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("should auto-detect source dialect when not provided")
        fun shouldAutoDetectSourceDialectWhenNotProvided() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            val targetDialect = SqlDialect.TRINO
            val bigQueryMetric =
                MetricEntity(
                    name = testMetric.name,
                    owner = testMetric.owner,
                    team = testMetric.team,
                    description = testMetric.description,
                    sql = "SELECT DATE_SUB(`users`.`created_at`, INTERVAL 1 DAY) FROM `users`",
                    tags = testMetric.tags,
                    dependencies = testMetric.dependencies,
                )

            val parserResult =
                TranspileResponse(
                    success = true,
                    transpiledSql = "SELECT date_add('day', -1, users.created_at) FROM users",
                    appliedTransformations = emptyList(),
                    warnings = emptyList(),
                    errorMessage = null,
                )

            every { metricService.getMetric(metricName) } returns bigQueryMetric
            every {
                transpileRuleRepositoryDsl.findApplicableRules(
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = targetDialect,
                    orderByPriority = true,
                )
            } returns emptyList()
            every {
                basecampParserClient.transpileSQL(
                    sql = bigQueryMetric.sql,
                    sourceDialect = SqlDialect.BIGQUERY.name.lowercase(),
                    targetDialect = targetDialect.name.lowercase(),
                    rules = emptyList(),
                )
            } returns parserResult

            // When
            val result =
                transpileService.transpileMetric(
                    metricName = metricName,
                    targetDialect = targetDialect,
                    sourceDialect = null, // Auto-detect
                )

            // Then
            assertThat(result.sourceDialect).isEqualTo(SqlDialect.BIGQUERY.name.lowercase())
            assertThat(result.targetDialect).isEqualTo(targetDialect.name.lowercase())
            assertThat(result.transpiledSql).isEqualTo(parserResult.transpiledSql)
        }

        @Test
        @DisplayName("should throw MetricNotFoundException when metric not found")
        fun shouldThrowMetricNotFoundExceptionWhenMetricNotFound() {
            // Given
            val metricName = "non_existent_metric"
            val targetDialect = SqlDialect.TRINO

            every { metricService.getMetric(metricName) } returns null

            // When & Then
            val exception =
                assertThrows<TranspileException> {
                    transpileService.transpileMetric(
                        metricName = metricName,
                        targetDialect = targetDialect,
                    )
                }

            assertThat(exception.cause).isInstanceOf(MetricNotFoundException::class.java)

            assertThat(exception.message).contains(metricName)
            verify(exactly = 1) { metricService.getMetric(metricName) }
        }

        @Test
        @DisplayName("should throw TranspileException when parser fails")
        fun shouldThrowTranspileExceptionWhenParserFails() {
            // Given
            val metricName = "test_catalog.test_schema.test_metric"
            val targetDialect = SqlDialect.TRINO
            val sourceDialect = SqlDialect.BIGQUERY

            val failedParserResult =
                TranspileResponse(
                    success = false,
                    transpiledSql = "",
                    appliedTransformations = emptyList(),
                    warnings = emptyList(),
                    errorMessage = "Syntax error in SQL query",
                )

            every { metricService.getMetric(metricName) } returns testMetric
            every {
                transpileRuleRepositoryDsl.findApplicableRules(any(), any(), any())
            } returns emptyList()
            every {
                basecampParserClient.transpileSQL(any(), any(), any(), any())
            } returns failedParserResult

            // When & Then
            val exception =
                assertThrows<TranspileException> {
                    transpileService.transpileMetric(
                        metricName = metricName,
                        targetDialect = targetDialect,
                        sourceDialect = sourceDialect,
                    )
                }

            assertThat(exception.message).contains("Failed to transpile metric")
            assertThat(exception.message).contains("Syntax error in SQL query")
        }
    }

    @Nested
    @DisplayName("transpileDataset")
    inner class TranspileDataset {
        @Test
        @DisplayName("should transpile dataset SQL successfully")
        fun shouldTranspileDatasetSQLSuccessfully() {
            // Given
            val datasetName = "test_catalog.test_schema.test_dataset"
            val targetDialect = SqlDialect.TRINO
            val sourceDialect = SqlDialect.BIGQUERY

            val parserResult =
                TranspileResponse(
                    success = true,
                    transpiledSql = "SELECT id, name FROM users WHERE date >= CAST('2024-01-01' AS TIMESTAMP)",
                    appliedTransformations = emptyList(),
                    warnings = emptyList(),
                    errorMessage = null,
                )

            every { datasetService.getDataset(datasetName) } returns testDataset
            every {
                transpileRuleRepositoryDsl.findApplicableRules(
                    any(),
                    any(),
                    any(),
                )
            } returns emptyList()
            every {
                basecampParserClient.transpileSQL(any(), any(), any(), any())
            } returns parserResult

            // When
            val result =
                transpileService.transpileDataset(
                    datasetName = datasetName,
                    targetDialect = targetDialect,
                    sourceDialect = sourceDialect,
                )

            // Then
            assertThat(result.datasetName).isEqualTo(datasetName)
            assertThat(result.sourceDialect).isEqualTo(sourceDialect.name.lowercase())
            assertThat(result.targetDialect).isEqualTo(targetDialect.name.lowercase())
            assertThat(result.originalSql).isEqualTo(testDataset.sql)
            assertThat(result.transpiledSql).isEqualTo(parserResult.transpiledSql)
        }

        @Test
        @DisplayName("should throw DatasetNotFoundException when dataset not found")
        fun shouldThrowDatasetNotFoundExceptionWhenDatasetNotFound() {
            // Given
            val datasetName = "non_existent_dataset"
            val targetDialect = SqlDialect.TRINO

            every { datasetService.getDataset(datasetName) } returns null

            // When & Then
            val exception =
                assertThrows<TranspileException> {
                    transpileService.transpileDataset(
                        datasetName = datasetName,
                        targetDialect = targetDialect,
                    )
                }

            assertThat(exception.cause).isInstanceOf(DatasetNotFoundException::class.java)
            assertThat(exception.message).contains(datasetName)
        }
    }
}
