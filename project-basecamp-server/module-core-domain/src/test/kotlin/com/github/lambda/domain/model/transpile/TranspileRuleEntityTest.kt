package com.github.lambda.domain.model.transpile

import com.github.lambda.common.enums.SqlDialect
import com.github.lambda.domain.entity.transpile.TranspileRuleEntity
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * TranspileRuleEntity Unit Tests
 *
 * Tests entity business logic and validation methods.
 */
@DisplayName("TranspileRuleEntity Unit Tests")
class TranspileRuleEntityTest {
    @Nested
    @DisplayName("Constructor and Basic Properties")
    inner class ConstructorAndBasicProperties {
        @Test
        @DisplayName("should create transpile rule entity with required fields")
        fun shouldCreateTranspileRuleEntityWithRequiredFields() {
            // Given & When
            val rule =
                TranspileRuleEntity(
                    name = "bigquery_to_trino_datetime",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "DATETIME\\((.+?)\\)",
                    replacement = "CAST($1 AS TIMESTAMP)",
                )

            // Then
            assertThat(rule.name).isEqualTo("bigquery_to_trino_datetime")
            assertThat(rule.fromDialect).isEqualTo(SqlDialect.BIGQUERY)
            assertThat(rule.toDialect).isEqualTo(SqlDialect.TRINO)
            assertThat(rule.pattern).isEqualTo("DATETIME\\((.+?)\\)")
            assertThat(rule.replacement).isEqualTo("CAST($1 AS TIMESTAMP)")
            assertThat(rule.priority).isEqualTo(100) // Default value
            assertThat(rule.enabled).isTrue() // Default value
            assertThat(rule.description).isNull()
        }

        @Test
        @DisplayName("should create transpile rule entity with all fields")
        fun shouldCreateTranspileRuleEntityWithAllFields() {
            // Given & When
            val rule =
                TranspileRuleEntity(
                    name = "custom_rule",
                    fromDialect = SqlDialect.TRINO,
                    toDialect = SqlDialect.BIGQUERY,
                    pattern = "LIMIT (\\d+)",
                    replacement = "LIMIT \\$1",
                    priority = 200,
                    enabled = false,
                    description = "Custom transformation rule",
                )

            // Then
            assertThat(rule.name).isEqualTo("custom_rule")
            assertThat(rule.fromDialect).isEqualTo(SqlDialect.TRINO)
            assertThat(rule.toDialect).isEqualTo(SqlDialect.BIGQUERY)
            assertThat(rule.pattern).isEqualTo("LIMIT (\\d+)")
            assertThat(rule.replacement).isEqualTo("LIMIT \\$1")
            assertThat(rule.priority).isEqualTo(200)
            assertThat(rule.enabled).isFalse()
            assertThat(rule.description).isEqualTo("Custom transformation rule")
        }
    }

    @Nested
    @DisplayName("appliesToDialects")
    inner class AppliesToDialects {
        @Test
        @DisplayName("should return true for exact dialect match")
        fun shouldReturnTrueForExactDialectMatch() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "test",
                    replacement = "test",
                    enabled = true,
                )

            // When
            val result =
                rule.appliesToDialects(
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                )

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return true for ANY source dialect")
        fun shouldReturnTrueForAnySourceDialect() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.ANY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "test",
                    replacement = "test",
                    enabled = true,
                )

            // When
            val result =
                rule.appliesToDialects(
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                )

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return true for ANY target dialect")
        fun shouldReturnTrueForAnyTargetDialect() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.ANY,
                    pattern = "test",
                    replacement = "test",
                    enabled = true,
                )

            // When
            val result =
                rule.appliesToDialects(
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                )

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return true for ANY both dialects")
        fun shouldReturnTrueForAnyBothDialects() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.ANY,
                    toDialect = SqlDialect.ANY,
                    pattern = "test",
                    replacement = "test",
                    enabled = true,
                )

            // When
            val result =
                rule.appliesToDialects(
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                )

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false for mismatched source dialect")
        fun shouldReturnFalseForMismatchedSourceDialect() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "test",
                    replacement = "test",
                    enabled = true,
                )

            // When
            val result =
                rule.appliesToDialects(
                    fromDialect = SqlDialect.TRINO,
                    toDialect = SqlDialect.BIGQUERY,
                )

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for mismatched target dialect")
        fun shouldReturnFalseForMismatchedTargetDialect() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "test",
                    replacement = "test",
                    enabled = true,
                )

            // When
            val result =
                rule.appliesToDialects(
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.BIGQUERY,
                )

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false when rule is disabled")
        fun shouldReturnFalseWhenRuleIsDisabled() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "test",
                    replacement = "test",
                    enabled = false,
                )

            // When
            val result =
                rule.appliesToDialects(
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                )

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("applyTo")
    inner class ApplyTo {
        @Test
        @DisplayName("should apply regex replacement to SQL")
        fun shouldApplyRegexReplacementToSQL() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "datetime_conversion",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "DATETIME\\((.+?)\\)",
                    replacement = "CAST($1 AS TIMESTAMP)",
                    enabled = true,
                )

            val sql = "SELECT DATETIME(created_at) FROM users"

            // When
            val result = rule.applyTo(sql)

            // Then
            assertThat(result).isEqualTo("SELECT CAST(created_at AS TIMESTAMP) FROM users")
        }

        @Test
        @DisplayName("should apply multiple replacements in same SQL")
        fun shouldApplyMultipleReplacementsInSameSQL() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "function_conversion",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "FUNC\\((\\w+)\\)",
                    replacement = "NEW_FUNC($1)",
                    enabled = true,
                )

            val sql = "SELECT FUNC(col1), FUNC(col2) FROM table"

            // When
            val result = rule.applyTo(sql)

            // Then
            assertThat(result).isEqualTo("SELECT NEW_FUNC(col1), NEW_FUNC(col2) FROM table")
        }

        @Test
        @DisplayName("should return original SQL when pattern does not match")
        fun shouldReturnOriginalSQLWhenPatternDoesNotMatch() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "DATETIME\\((.+?)\\)",
                    replacement = "CAST($1 AS TIMESTAMP)",
                    enabled = true,
                )

            val sql = "SELECT * FROM users"

            // When
            val result = rule.applyTo(sql)

            // Then
            assertThat(result).isEqualTo(sql)
        }

        @Test
        @DisplayName("should return original SQL when rule is disabled")
        fun shouldReturnOriginalSQLWhenRuleIsDisabled() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "DATETIME\\((.+?)\\)",
                    replacement = "CAST($1 AS TIMESTAMP)",
                    enabled = false,
                )

            val sql = "SELECT DATETIME(created_at) FROM users"

            // When
            val result = rule.applyTo(sql)

            // Then
            assertThat(result).isEqualTo(sql) // No change because rule is disabled
        }

        @Test
        @DisplayName("should handle complex regex patterns")
        fun shouldHandleComplexRegexPatterns() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "complex_pattern",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "EXTRACT\\((\\w+)\\s+FROM\\s+(\\w+)\\)",
                    replacement = "DATE_PART('$1', $2)",
                    enabled = true,
                )

            val sql = "SELECT EXTRACT(YEAR FROM created_at) FROM users"

            // When
            val result = rule.applyTo(sql)

            // Then
            assertThat(result).isEqualTo("SELECT DATE_PART('YEAR', created_at) FROM users")
        }
    }

    @Nested
    @DisplayName("matchesSQL")
    inner class MatchesSQL {
        @Test
        @DisplayName("should return true when pattern matches SQL")
        fun shouldReturnTrueWhenPatternMatchesSQL() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "DATETIME\\((.+?)\\)",
                    replacement = "CAST($1 AS TIMESTAMP)",
                    enabled = true,
                )

            val sql = "SELECT DATETIME(created_at) FROM users"

            // When
            val result = rule.matchesSQL(sql)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false when pattern does not match SQL")
        fun shouldReturnFalseWhenPatternDoesNotMatchSQL() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "DATETIME\\((.+?)\\)",
                    replacement = "CAST($1 AS TIMESTAMP)",
                    enabled = true,
                )

            val sql = "SELECT * FROM users"

            // When
            val result = rule.matchesSQL(sql)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false when rule is disabled")
        fun shouldReturnFalseWhenRuleIsDisabled() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "DATETIME\\((.+?)\\)",
                    replacement = "CAST($1 AS TIMESTAMP)",
                    enabled = false,
                )

            val sql = "SELECT DATETIME(created_at) FROM users"

            // When
            val result = rule.matchesSQL(sql)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should handle case-sensitive patterns")
        fun shouldHandleCaseSensitivePatterns() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "case_sensitive_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "datetime\\((.+?)\\)", // lowercase
                    replacement = "CAST($1 AS TIMESTAMP)",
                    enabled = true,
                )

            val sqlLower = "SELECT datetime(created_at) FROM users"
            val sqlUpper = "SELECT DATETIME(created_at) FROM users"

            // When
            val resultLower = rule.matchesSQL(sqlLower)
            val resultUpper = rule.matchesSQL(sqlUpper)

            // Then
            assertThat(resultLower).isTrue()
            assertThat(resultUpper).isFalse() // Case-sensitive mismatch
        }

        @Test
        @DisplayName("should find partial matches in complex SQL")
        fun shouldFindPartialMatchesInComplexSQL() {
            // Given
            val rule =
                TranspileRuleEntity(
                    name = "test_rule",
                    fromDialect = SqlDialect.BIGQUERY,
                    toDialect = SqlDialect.TRINO,
                    pattern = "COUNT\\(\\*\\)",
                    replacement = "COUNT(1)",
                    enabled = true,
                )

            val sql =
                """
                SELECT
                    user_id,
                    COUNT(*) as user_count,
                    AVG(score)
                FROM user_scores
                WHERE created_at > '2024-01-01'
                GROUP BY user_id
                HAVING COUNT(*) > 5
                """.trimIndent()

            // When
            val result = rule.matchesSQL(sql)

            // Then
            assertThat(result).isTrue()
        }
    }
}
