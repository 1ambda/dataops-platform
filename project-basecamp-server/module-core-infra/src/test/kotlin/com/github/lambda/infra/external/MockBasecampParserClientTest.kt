package com.github.lambda.infra.external

import com.github.lambda.domain.external.TranspileRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * MockBasecampParserClient Unit Tests
 *
 * Tests the mock implementation of SQL parsing, validation, and transpilation functionality.
 * Uses deterministic mode to ensure consistent test results.
 */
@DisplayName("MockBasecampParserClient Unit Tests")
class MockBasecampParserClientTest {
    private lateinit var client: MockBasecampParserClient

    @BeforeEach
    fun setUp() {
        client = MockBasecampParserClient()
    }

    @Nested
    @DisplayName("parseSQL")
    inner class ParseSQL {
        @Test
        @DisplayName("should parse simple SELECT query successfully")
        fun shouldParseSimpleSelectQuerySuccessfully() {
            // Given
            val sql = "SELECT id, name FROM schema.users"

            // When
            val result = client.parseSQL(sql, "trino")

            // Then
            assertThat(result.success).isTrue()
            assertThat(result.sourceTables).contains("schema.users")
            assertThat(result.targetTables).isEmpty()
            assertThat(result.errorMessage).isNull()
        }

        @Test
        @DisplayName("should parse INSERT query successfully")
        fun shouldParseInsertQuerySuccessfully() {
            // Given
            val sql = "INSERT INTO schema.users (name, email) VALUES ('John', 'john@example.com')"

            // When
            val result = client.parseSQL(sql, "trino")

            // Then
            assertThat(result.success).isTrue()
            assertThat(result.targetTables).contains("schema.users")
        }

        @Test
        @DisplayName("should extract multiple source tables from JOIN query")
        fun shouldExtractMultipleSourceTablesFromJoinQuery() {
            // Given
            val sql = "SELECT u.name, p.title FROM schema.users u JOIN schema.profiles p ON u.id = p.user_id"

            // When
            val result = client.parseSQL(sql, "trino")

            // Then
            assertThat(result.success).isTrue()
            assertThat(result.sourceTables).containsExactlyInAnyOrder("schema.users", "schema.profiles")
        }
    }

    @Nested
    @DisplayName("validateSQL")
    inner class ValidateSQL {
        @Test
        @DisplayName("should validate correct SQL")
        fun shouldValidateCorrectSQL() {
            // Given
            val sql = "SELECT * FROM users WHERE id = 1"

            // When
            val result = client.validateSQL(sql)

            // Then
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("getSupportedDialects")
    inner class GetSupportedDialects {
        @Test
        @DisplayName("should return supported dialects")
        fun shouldReturnSupportedDialects() {
            // When
            val dialects = client.getSupportedDialects()

            // Then
            assertThat(dialects).isNotEmpty()
            assertThat(dialects).contains("bigquery", "trino")
        }
    }

    @Nested
    @DisplayName("transpileSQL")
    inner class TranspileSQL {
        @Test
        @DisplayName("should transpile SQL successfully")
        fun shouldTranspileSQLSuccessfully() {
            // Given
            val sql = "SELECT * FROM schema.users"
            val sourceDialect = "bigquery"
            val targetDialect = "trino"

            // When
            val result = client.transpileSQL(sql, sourceDialect, targetDialect)

            // Then
            assertThat(result.success).isTrue()
            assertThat(result.transpiledSql).isNotEmpty()
            assertThat(result.errorMessage).isNull()
        }

        @Test
        @DisplayName("should transpile with custom rules")
        fun shouldTranspileWithCustomRules() {
            // Given
            val sql = "SELECT DATETIME('2023-01-01') FROM schema.users"
            val sourceDialect = "bigquery"
            val targetDialect = "trino"
            val rules =
                listOf(
                    TranspileRule(
                        name = "datetime_to_cast",
                        pattern = "DATETIME\\\\((.+?)\\\\)",
                        replacement = "CAST(\\\\$1 AS TIMESTAMP)",
                    ),
                )

            // When
            val result = client.transpileSQL(sql, sourceDialect, targetDialect, rules)

            // Then
            assertThat(result.success).isTrue()
            assertThat(result.transpiledSql).isNotEmpty()
        }
    }
}
