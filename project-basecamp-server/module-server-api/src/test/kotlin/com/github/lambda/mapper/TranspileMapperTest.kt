package com.github.lambda.mapper

import com.github.lambda.api.dto.transpile.*
import com.github.lambda.domain.model.transpile.SqlDialect
import com.github.lambda.domain.model.transpile.TranspileRuleEntity
import com.github.lambda.domain.service.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * TranspileMapper Unit Tests
 *
 * Tests DTO ↔ Domain mapping functionality.
 */
@DisplayName("TranspileMapper Unit Tests")
class TranspileMapperTest {
    private val mapper = TranspileMapper()

    private lateinit var testTranspileRule: TranspileRuleEntity
    private lateinit var testTranspileRulesResult: TranspileRulesResult
    private lateinit var testMetricTranspileResult: MetricTranspileResult
    private lateinit var testDatasetTranspileResult: DatasetTranspileResult
    private lateinit var testAppliedRule: AppliedRule
    private lateinit var testTranspileWarning: TranspileWarning

    @BeforeEach
    fun setUp() {
        testTranspileRule =
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

        testAppliedRule =
            AppliedRule(
                name = "bigquery_to_trino_datetime",
                source = "DATETIME(created_at)",
                target = "CAST(created_at AS TIMESTAMP)",
            )

        testTranspileWarning =
            TranspileWarning(
                type = "PARAMETER_SUBSTITUTION",
                message = "Parameter '{{date}}' was substituted",
                line = 1,
                column = 45,
            )

        testTranspileRulesResult =
            TranspileRulesResult(
                version = "1.0.0",
                rules = listOf(testTranspileRule),
                metadata =
                    TranspileMetadata(
                        createdAt = Instant.parse("2024-01-01T09:00:00Z"),
                        createdBy = "system",
                        totalRules = 1,
                        cacheTtlSeconds = 3600,
                    ),
            )

        testMetricTranspileResult =
            MetricTranspileResult(
                metricName = "test_catalog.test_schema.test_metric",
                sourceDialect = "bigquery",
                targetDialect = "trino",
                originalSql = "SELECT COUNT(*) FROM users WHERE created_at >= DATETIME('2024-01-01')",
                transpiledSql = "SELECT COUNT(*) FROM users WHERE created_at >= CAST('2024-01-01' AS TIMESTAMP)",
                appliedRules = listOf(testAppliedRule),
                warnings = listOf(testTranspileWarning),
                transpiledAt = Instant.parse("2024-01-01T10:00:00Z"),
                durationMs = 250,
            )

        testDatasetTranspileResult =
            DatasetTranspileResult(
                datasetName = "test_catalog.test_schema.test_dataset",
                sourceDialect = "bigquery",
                targetDialect = "trino",
                originalSql = "SELECT id, name FROM users WHERE created_at >= DATETIME('2024-01-01')",
                transpiledSql = "SELECT id, name FROM users WHERE created_at >= CAST('2024-01-01' AS TIMESTAMP)",
                appliedRules = listOf(testAppliedRule),
                warnings = listOf(testTranspileWarning),
                transpiledAt = Instant.parse("2024-01-01T10:00:00Z"),
                durationMs = 180,
            )
    }

    @Nested
    @DisplayName("toTranspileRulesDto")
    inner class ToTranspileRulesDto {
        @Test
        @DisplayName("should convert TranspileRulesResult to TranspileRulesDto successfully")
        fun shouldConvertTranspileRulesResultToTranspileRulesDto() {
            // When
            val result = mapper.toTranspileRulesDto(testTranspileRulesResult)

            // Then
            assertThat(result).isNotNull()
            assertThat(result.version).isEqualTo("1.0.0")
            assertThat(result.rules).hasSize(1)

            val ruleDto = result.rules.first()
            assertThat(ruleDto.name).isEqualTo(testTranspileRule.name)
            assertThat(ruleDto.fromDialect).isEqualTo(testTranspileRule.fromDialect.name.lowercase())
            assertThat(ruleDto.toDialect).isEqualTo(testTranspileRule.toDialect.name.lowercase())
            assertThat(ruleDto.pattern).isEqualTo(testTranspileRule.pattern)
            assertThat(ruleDto.replacement).isEqualTo(testTranspileRule.replacement)
            assertThat(ruleDto.priority).isEqualTo(testTranspileRule.priority)
            assertThat(ruleDto.enabled).isEqualTo(testTranspileRule.enabled)
            assertThat(ruleDto.description).isEqualTo(testTranspileRule.description)

            assertThat(result.metadata).isNotNull()
            assertThat(result.metadata.createdAt).isEqualTo(testTranspileRulesResult.metadata.createdAt)
            assertThat(result.metadata.createdBy).isEqualTo(testTranspileRulesResult.metadata.createdBy)
            assertThat(result.metadata.totalRules).isEqualTo(testTranspileRulesResult.metadata.totalRules)
            assertThat(result.metadata.cacheTtlSeconds).isEqualTo(testTranspileRulesResult.metadata.cacheTtlSeconds)
        }

        @Test
        @DisplayName("should handle empty rules list")
        fun shouldHandleEmptyRulesList() {
            // Given
            val emptyRulesResult =
                testTranspileRulesResult.copy(
                    rules = emptyList(),
                    metadata = testTranspileRulesResult.metadata.copy(totalRules = 0),
                )

            // When
            val result = mapper.toTranspileRulesDto(emptyRulesResult)

            // Then
            assertThat(result.version).isEqualTo("1.0.0")
            assertThat(result.rules).isEmpty()
            assertThat(result.metadata.totalRules).isEqualTo(0)
        }

        @Test
        @DisplayName("should handle multiple rules with different dialects")
        fun shouldHandleMultipleRulesWithDifferentDialects() {
            // Given
            val additionalRule =
                TranspileRuleEntity(
                    name = "mysql_to_postgresql_limit",
                    fromDialect = SqlDialect.MYSQL,
                    toDialect = SqlDialect.POSTGRESQL,
                    pattern = "LIMIT (\\d+)",
                    replacement = "LIMIT \\$1",
                    priority = 200,
                    enabled = false,
                    description = "MySQL to PostgreSQL LIMIT",
                )

            val multipleRulesResult =
                testTranspileRulesResult.copy(
                    rules = listOf(testTranspileRule, additionalRule),
                    metadata = testTranspileRulesResult.metadata.copy(totalRules = 2),
                )

            // When
            val result = mapper.toTranspileRulesDto(multipleRulesResult)

            // Then
            assertThat(result.rules).hasSize(2)
            assertThat(result.metadata.totalRules).isEqualTo(2)

            val firstRule = result.rules[0]
            assertThat(firstRule.name).isEqualTo("bigquery_to_trino_datetime")
            assertThat(firstRule.fromDialect).isEqualTo("bigquery")
            assertThat(firstRule.toDialect).isEqualTo("trino")
            assertThat(firstRule.enabled).isTrue()

            val secondRule = result.rules[1]
            assertThat(secondRule.name).isEqualTo("mysql_to_postgresql_limit")
            assertThat(secondRule.fromDialect).isEqualTo("mysql")
            assertThat(secondRule.toDialect).isEqualTo("postgresql")
            assertThat(secondRule.enabled).isFalse()
        }
    }

    @Nested
    @DisplayName("toTranspileResultDto - Metric")
    inner class ToTranspileResultDtoMetric {
        @Test
        @DisplayName("should convert MetricTranspileResult to TranspileResultDto successfully")
        fun shouldConvertMetricTranspileResultToTranspileResultDto() {
            // When
            val result = mapper.toTranspileResultDto(testMetricTranspileResult)

            // Then
            assertThat(result).isNotNull()
            assertThat(result.resourceType).isEqualTo("metric")
            assertThat(result.resourceName).isEqualTo(testMetricTranspileResult.metricName)
            assertThat(result.sourceDialect).isEqualTo(testMetricTranspileResult.sourceDialect)
            assertThat(result.targetDialect).isEqualTo(testMetricTranspileResult.targetDialect)
            assertThat(result.originalSql).isEqualTo(testMetricTranspileResult.originalSql)
            assertThat(result.transpiledSql).isEqualTo(testMetricTranspileResult.transpiledSql)
            assertThat(result.transpiledAt).isEqualTo(testMetricTranspileResult.transpiledAt)
            assertThat(result.durationMs).isEqualTo(testMetricTranspileResult.durationMs)

            assertThat(result.appliedRules).hasSize(1)
            val appliedRuleDto = result.appliedRules.first()
            assertThat(appliedRuleDto.name).isEqualTo(testAppliedRule.name)
            assertThat(appliedRuleDto.source).isEqualTo(testAppliedRule.source)
            assertThat(appliedRuleDto.target).isEqualTo(testAppliedRule.target)

            assertThat(result.warnings).hasSize(1)
            val warningDto = result.warnings.first()
            assertThat(warningDto.type).isEqualTo(testTranspileWarning.type)
            assertThat(warningDto.message).isEqualTo(testTranspileWarning.message)
            assertThat(warningDto.line).isEqualTo(testTranspileWarning.line)
            assertThat(warningDto.column).isEqualTo(testTranspileWarning.column)
        }

        @Test
        @DisplayName("should handle metric result with no applied rules or warnings")
        fun shouldHandleMetricResultWithNoAppliedRulesOrWarnings() {
            // Given
            val simpleMetricResult =
                testMetricTranspileResult.copy(
                    appliedRules = emptyList(),
                    warnings = emptyList(),
                )

            // When
            val result = mapper.toTranspileResultDto(simpleMetricResult)

            // Then
            assertThat(result.resourceType).isEqualTo("metric")
            assertThat(result.appliedRules).isEmpty()
            assertThat(result.warnings).isEmpty()
        }
    }

    @Nested
    @DisplayName("toTranspileResultDto - Dataset")
    inner class ToTranspileResultDtoDataset {
        @Test
        @DisplayName("should convert DatasetTranspileResult to TranspileResultDto successfully")
        fun shouldConvertDatasetTranspileResultToTranspileResultDto() {
            // When
            val result = mapper.toTranspileResultDto(testDatasetTranspileResult)

            // Then
            assertThat(result).isNotNull()
            assertThat(result.resourceType).isEqualTo("dataset")
            assertThat(result.resourceName).isEqualTo(testDatasetTranspileResult.datasetName)
            assertThat(result.sourceDialect).isEqualTo(testDatasetTranspileResult.sourceDialect)
            assertThat(result.targetDialect).isEqualTo(testDatasetTranspileResult.targetDialect)
            assertThat(result.originalSql).isEqualTo(testDatasetTranspileResult.originalSql)
            assertThat(result.transpiledSql).isEqualTo(testDatasetTranspileResult.transpiledSql)
            assertThat(result.transpiledAt).isEqualTo(testDatasetTranspileResult.transpiledAt)
            assertThat(result.durationMs).isEqualTo(testDatasetTranspileResult.durationMs)

            assertThat(result.appliedRules).hasSize(1)
            assertThat(result.warnings).hasSize(1)
        }

        @Test
        @DisplayName("should handle dataset result with multiple applied rules")
        fun shouldHandleDatasetResultWithMultipleAppliedRules() {
            // Given
            val additionalRule =
                AppliedRule(
                    name = "timestamp_conversion",
                    source = "TIMESTAMP('2024-01-01')",
                    target = "CAST('2024-01-01' AS TIMESTAMP)",
                )

            val multipleRulesDatasetResult =
                testDatasetTranspileResult.copy(
                    appliedRules = listOf(testAppliedRule, additionalRule),
                )

            // When
            val result = mapper.toTranspileResultDto(multipleRulesDatasetResult)

            // Then
            assertThat(result.resourceType).isEqualTo("dataset")
            assertThat(result.appliedRules).hasSize(2)
            assertThat(result.appliedRules[0].name).isEqualTo("bigquery_to_trino_datetime")
            assertThat(result.appliedRules[1].name).isEqualTo("timestamp_conversion")
        }

        @Test
        @DisplayName("should handle dataset result with multiple warnings")
        fun shouldHandleDatasetResultWithMultipleWarnings() {
            // Given
            val additionalWarning =
                TranspileWarning(
                    type = "DEPRECATED_FUNCTION",
                    message = "Function FUNC is deprecated",
                    line = 2,
                    column = 15,
                )

            val multipleWarningsDatasetResult =
                testDatasetTranspileResult.copy(
                    warnings = listOf(testTranspileWarning, additionalWarning),
                )

            // When
            val result = mapper.toTranspileResultDto(multipleWarningsDatasetResult)

            // Then
            assertThat(result.warnings).hasSize(2)
            assertThat(result.warnings[0].type).isEqualTo("PARAMETER_SUBSTITUTION")
            assertThat(result.warnings[1].type).isEqualTo("DEPRECATED_FUNCTION")
        }
    }

    @Nested
    @DisplayName("Dialect Conversion")
    inner class DialectConversion {
        @Test
        @DisplayName("should properly convert all SQL dialect enums to lowercase")
        fun shouldProperlyConvertAllSQLDialectEnumsToLowercase() {
            // Given
            val rules =
                SqlDialect.values().map { dialect ->
                    TranspileRuleEntity(
                        name = "test_${dialect.name.lowercase()}",
                        fromDialect = dialect,
                        toDialect = SqlDialect.GENERIC,
                        pattern = "test",
                        replacement = "test",
                    )
                }

            val rulesResult =
                testTranspileRulesResult.copy(
                    rules = rules,
                    metadata = testTranspileRulesResult.metadata.copy(totalRules = rules.size),
                )

            // When
            val result = mapper.toTranspileRulesDto(rulesResult)

            // Then
            result.rules.forEach { ruleDto ->
                assertThat(ruleDto.fromDialect).isLowerCase()
                assertThat(ruleDto.toDialect).isLowerCase()
                assertThat(ruleDto.fromDialect).isEqualTo(ruleDto.fromDialect.lowercase())
                assertThat(ruleDto.toDialect).isEqualTo(ruleDto.toDialect.lowercase())
            }
        }
    }

    @Nested
    @DisplayName("Null and Edge Cases")
    inner class NullAndEdgeCases {
        @Test
        @DisplayName("should handle rule with null description")
        fun shouldHandleRuleWithNullDescription() {
            // Given
            val ruleWithNullDescription = testTranspileRule.copy(description = null)
            val rulesResult = testTranspileRulesResult.copy(rules = listOf(ruleWithNullDescription))

            // When
            val result = mapper.toTranspileRulesDto(rulesResult)

            // Then
            assertThat(result.rules.first().description).isNull()
        }

        @Test
        @DisplayName("should handle zero duration")
        fun shouldHandleZeroDuration() {
            // Given
            val quickMetricResult = testMetricTranspileResult.copy(durationMs = 0)

            // When
            val result = mapper.toTranspileResultDto(quickMetricResult)

            // Then
            assertThat(result.durationMs).isEqualTo(0)
        }

        @Test
        @DisplayName("should handle empty SQL strings")
        fun shouldHandleEmptySQLStrings() {
            // Given
            val emptyMetricResult =
                testMetricTranspileResult.copy(
                    originalSql = "",
                    transpiledSql = "",
                )

            // When
            val result = mapper.toTranspileResultDto(emptyMetricResult)

            // Then
            assertThat(result.originalSql).isEmpty()
            assertThat(result.transpiledSql).isEmpty()
        }
    }
}
