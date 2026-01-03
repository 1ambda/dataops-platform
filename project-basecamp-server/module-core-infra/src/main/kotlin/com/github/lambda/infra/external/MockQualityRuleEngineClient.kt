package com.github.lambda.infra.external

import com.github.lambda.common.exception.QualityRuleEngineException
import com.github.lambda.domain.external.QualityRuleEngineClient
import com.github.lambda.domain.model.quality.AcceptedValuesConfig
import com.github.lambda.domain.model.quality.ExpressionConfig
import com.github.lambda.domain.model.quality.GenerateSqlRequest
import com.github.lambda.domain.model.quality.GenerateSqlResponse
import com.github.lambda.domain.model.quality.RelationshipsConfig
import com.github.lambda.domain.model.quality.RowCountConfig
import com.github.lambda.domain.model.quality.TestType
import org.springframework.stereotype.Repository

/**
 * Mock Quality Rule Engine Client Implementation
 *
 * Provides mock SQL generation responses for quality tests while the actual
 * project-basecamp-parser service is not yet implemented.
 */
@Repository("qualityRuleEngineClient")
class MockQualityRuleEngineClient : QualityRuleEngineClient {
    override fun generateSql(request: GenerateSqlRequest): GenerateSqlResponse =
        when (request.testType) {
            TestType.NOT_NULL -> generateNotNullSql(request)
            TestType.UNIQUE -> generateUniqueSql(request)
            TestType.ACCEPTED_VALUES -> generateAcceptedValuesSql(request)
            TestType.RELATIONSHIPS -> generateRelationshipsSql(request)
            TestType.EXPRESSION -> generateExpressionSql(request)
            TestType.ROW_COUNT -> generateRowCountSql(request)
            TestType.SINGULAR -> generateSingularSql(request)
        }

    override fun isAvailable(): Boolean = true

    private fun generateNotNullSql(request: GenerateSqlRequest): GenerateSqlResponse {
        val column =
            request.column ?: throw QualityRuleEngineException(
                "Column is required for NOT_NULL test",
            )

        val sql =
            """
            SELECT
                COUNT(*) FILTER (WHERE $column IS NULL) as failed_rows,
                COUNT(*) as total_rows
            FROM ${request.resourceName}
            """.trimIndent()

        val sampleFailuresSql =
            """
            SELECT *
            FROM ${request.resourceName}
            WHERE $column IS NULL
            LIMIT 5
            """.trimIndent()

        return GenerateSqlResponse(sql, sampleFailuresSql)
    }

    private fun generateUniqueSql(request: GenerateSqlRequest): GenerateSqlResponse {
        val column =
            request.column ?: throw QualityRuleEngineException(
                "Column is required for UNIQUE test",
            )

        val sql =
            """
            SELECT
                COUNT(*) - COUNT(DISTINCT $column) as failed_rows,
                COUNT(*) as total_rows
            FROM ${request.resourceName}
            """.trimIndent()

        val sampleFailuresSql =
            """
            SELECT $column, COUNT(*) as duplicate_count
            FROM ${request.resourceName}
            GROUP BY $column
            HAVING COUNT(*) > 1
            LIMIT 5
            """.trimIndent()

        return GenerateSqlResponse(sql, sampleFailuresSql)
    }

    private fun generateAcceptedValuesSql(request: GenerateSqlRequest): GenerateSqlResponse {
        val column =
            request.column ?: throw QualityRuleEngineException(
                "Column is required for ACCEPTED_VALUES test",
            )

        val config = parseAcceptedValuesConfig(request.config)
        val acceptedValuesList = config.values.joinToString(", ") { "'$it'" }

        val sql =
            """
            SELECT
                COUNT(*) FILTER (WHERE $column NOT IN ($acceptedValuesList)) as failed_rows,
                COUNT(*) as total_rows
            FROM ${request.resourceName}
            """.trimIndent()

        val sampleFailuresSql =
            """
            SELECT DISTINCT $column
            FROM ${request.resourceName}
            WHERE $column NOT IN ($acceptedValuesList)
            LIMIT 5
            """.trimIndent()

        return GenerateSqlResponse(sql, sampleFailuresSql)
    }

    private fun generateRelationshipsSql(request: GenerateSqlRequest): GenerateSqlResponse {
        val column =
            request.column ?: throw QualityRuleEngineException(
                "Column is required for RELATIONSHIPS test",
            )

        val config = parseRelationshipsConfig(request.config)

        val sql =
            """
            SELECT
                COUNT(src.$column) FILTER (
                    WHERE src.$column IS NOT NULL
                    AND ref.${config.toColumn} IS NULL
                ) as failed_rows,
                COUNT(src.$column) as total_rows
            FROM ${request.resourceName} src
            LEFT JOIN ${config.toTable} ref ON src.$column = ref.${config.toColumn}
            """.trimIndent()

        val sampleFailuresSql =
            """
            SELECT src.$column
            FROM ${request.resourceName} src
            LEFT JOIN ${config.toTable} ref ON src.$column = ref.${config.toColumn}
            WHERE src.$column IS NOT NULL
            AND ref.${config.toColumn} IS NULL
            LIMIT 5
            """.trimIndent()

        return GenerateSqlResponse(sql, sampleFailuresSql)
    }

    private fun generateExpressionSql(request: GenerateSqlRequest): GenerateSqlResponse {
        val config = parseExpressionConfig(request.config)

        val sql =
            """
            SELECT
                COUNT(*) FILTER (WHERE NOT (${config.expression})) as failed_rows,
                COUNT(*) as total_rows
            FROM ${request.resourceName}
            """.trimIndent()

        val sampleFailuresSql =
            """
            SELECT *
            FROM ${request.resourceName}
            WHERE NOT (${config.expression})
            LIMIT 5
            """.trimIndent()

        return GenerateSqlResponse(sql, sampleFailuresSql)
    }

    private fun generateRowCountSql(request: GenerateSqlRequest): GenerateSqlResponse {
        val config = parseRowCountConfig(request.config)

        val condition =
            buildList {
                config.min?.let { add("row_count < $it") }
                config.max?.let { add("row_count > $it") }
            }.joinToString(" OR ")

        val sql =
            """
            WITH row_count_check AS (
                SELECT COUNT(*) as row_count
                FROM ${request.resourceName}
            )
            SELECT
                CASE WHEN $condition THEN 1 ELSE 0 END as failed_rows,
                1 as total_rows
            FROM row_count_check
            """.trimIndent()

        return GenerateSqlResponse(sql)
    }

    private fun generateSingularSql(request: GenerateSqlRequest): GenerateSqlResponse {
        val sql =
            """
            WITH row_count_check AS (
                SELECT COUNT(*) as row_count
                FROM ${request.resourceName}
            )
            SELECT
                CASE WHEN row_count != 1 THEN 1 ELSE 0 END as failed_rows,
                1 as total_rows
            FROM row_count_check
            """.trimIndent()

        val sampleFailuresSql =
            """
            SELECT COUNT(*) as actual_row_count
            FROM ${request.resourceName}
            """.trimIndent()

        return GenerateSqlResponse(sql, sampleFailuresSql)
    }

    // === Configuration Parsing Helpers ===

    private fun parseAcceptedValuesConfig(config: Map<String, Any>): AcceptedValuesConfig {
        @Suppress("UNCHECKED_CAST")
        val values =
            config["values"] as? List<String>
                ?: throw QualityRuleEngineException("Missing 'values' in accepted_values config")

        return AcceptedValuesConfig(values)
    }

    private fun parseRelationshipsConfig(config: Map<String, Any>): RelationshipsConfig {
        val toTable =
            config["to_table"] as? String
                ?: throw QualityRuleEngineException("Missing 'to_table' in relationships config")
        val toColumn =
            config["to_column"] as? String
                ?: throw QualityRuleEngineException("Missing 'to_column' in relationships config")

        return RelationshipsConfig(toTable, toColumn)
    }

    private fun parseExpressionConfig(config: Map<String, Any>): ExpressionConfig {
        val expression =
            config["expression"] as? String
                ?: throw QualityRuleEngineException("Missing 'expression' in expression config")
        val description = config["description"] as? String

        return ExpressionConfig(expression, description)
    }

    private fun parseRowCountConfig(config: Map<String, Any>): RowCountConfig {
        val min = (config["min"] as? Number)?.toLong()
        val max = (config["max"] as? Number)?.toLong()

        return RowCountConfig(min, max)
    }
}
