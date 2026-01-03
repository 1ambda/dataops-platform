package com.github.lambda.infra.external

import com.github.lambda.domain.external.BasecampParserClient
import com.github.lambda.domain.external.SQLLineageResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Mock Basecamp Parser Client
 *
 * Provides mock SQL lineage parsing using simple regex patterns.
 * Used for development and testing when real basecamp-parser service is not available.
 */
@Component("basecampParserClient")
@ConditionalOnProperty(
    value = ["basecamp.parser.mock-mode"],
    havingValue = "true",
    matchIfMissing = true,
)
class MockBasecampParserClient : BasecampParserClient {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Simple regex patterns for table extraction
        private val FROM_PATTERN =
            Pattern.compile(
                """\bFROM\s+([`"]?[\w.]+[`"]?)""",
                Pattern.CASE_INSENSITIVE,
            )
        private val JOIN_PATTERN =
            Pattern.compile(
                """\bJOIN\s+([`"]?[\w.]+[`"]?)""",
                Pattern.CASE_INSENSITIVE,
            )
        private val WITH_PATTERN =
            Pattern.compile(
                """\bWITH\s+(\w+)\s+AS""",
                Pattern.CASE_INSENSITIVE,
            )
        private val INSERT_PATTERN =
            Pattern.compile(
                """\bINSERT\s+(?:INTO\s+)?([`"]?[\w.]+[`"]?)""",
                Pattern.CASE_INSENSITIVE,
            )
        private val CREATE_TABLE_PATTERN =
            Pattern.compile(
                """\bCREATE\s+(?:OR\s+REPLACE\s+)?(?:TEMP\s+|TEMPORARY\s+)?TABLE\s+([`"]?[\w.]+[`"]?)""",
                Pattern.CASE_INSENSITIVE,
            )

        private val SUPPORTED_DIALECTS = listOf("bigquery", "trino", "postgres", "mysql")

        // Mock table mappings for realistic lineage demos
        private val MOCK_TABLE_MAPPINGS =
            mapOf(
                "raw.events" to listOf("raw.user_events", "raw.system_events"),
                "analytics.users" to listOf("raw.users", "raw.user_profiles", "staging.user_enriched"),
                "analytics.orders" to listOf("raw.orders", "raw.payments", "staging.order_enriched"),
                "staging.user_enriched" to listOf("raw.users", "raw.user_profiles"),
                "staging.order_enriched" to listOf("raw.orders", "raw.payments"),
                "marts.customer_metrics" to listOf("analytics.users", "analytics.orders"),
                "marts.revenue_daily" to listOf("analytics.orders", "analytics.payments"),
            )
    }

    override fun parseSQL(
        sql: String,
        dialect: String,
    ): SQLLineageResult {
        logger.debug("Parsing SQL lineage for dialect: {}, SQL length: {}", dialect, sql.length)

        return try {
            if (!isDialectSupported(dialect)) {
                return SQLLineageResult.error("Unsupported SQL dialect: $dialect")
            }

            val sourceTables = extractSourceTables(sql)
            val targetTables = extractTargetTables(sql)

            // Add realistic mock dependencies if tables are known
            val enhancedSources = enhanceWithMockDependencies(sourceTables + targetTables)

            logger.debug(
                "Parsed lineage - Sources: {}, Targets: {}, Enhanced: {}",
                sourceTables,
                targetTables,
                enhancedSources,
            )

            SQLLineageResult.success(
                sourceTables = enhancedSources,
                targetTables = targetTables,
                columnLineage = extractMockColumnLineage(sql, enhancedSources, targetTables),
            )
        } catch (e: Exception) {
            logger.error("Failed to parse SQL lineage", e)
            SQLLineageResult.error("Failed to parse SQL: ${e.message}")
        }
    }

    override fun validateSQL(
        sql: String,
        dialect: String,
    ): Boolean {
        return try {
            if (!isDialectSupported(dialect)) {
                return false
            }

            // Simple validation - check for basic SQL keywords
            val normalizedSql = sql.trim().lowercase()
            val hasValidKeywords =
                normalizedSql.startsWith("select") ||
                    normalizedSql.startsWith("with") ||
                    normalizedSql.startsWith("insert") ||
                    normalizedSql.startsWith("create") ||
                    normalizedSql.startsWith("update") ||
                    normalizedSql.startsWith("delete")

            hasValidKeywords && sql.isNotBlank() && sql.length > 10
        } catch (e: Exception) {
            logger.warn("SQL validation failed: {}", e.message)
            false
        }
    }

    override fun getSupportedDialects(): List<String> = SUPPORTED_DIALECTS

    /**
     * Extract source tables (FROM, JOIN clauses)
     */
    private fun extractSourceTables(sql: String): List<String> {
        val tables = mutableSetOf<String>()

        // Extract FROM tables
        val fromMatcher = FROM_PATTERN.matcher(sql)
        while (fromMatcher.find()) {
            val tableName = fromMatcher.group(1).trim('`', '"', ' ')
            if (isValidTableName(tableName)) {
                tables.add(normalizeTableName(tableName))
            }
        }

        // Extract JOIN tables
        val joinMatcher = JOIN_PATTERN.matcher(sql)
        while (joinMatcher.find()) {
            val tableName = joinMatcher.group(1).trim('`', '"', ' ')
            if (isValidTableName(tableName)) {
                tables.add(normalizeTableName(tableName))
            }
        }

        // Remove CTE names (WITH clauses)
        val cteNames = extractCTENames(sql)
        tables.removeAll(cteNames.toSet())

        return tables.toList().sorted()
    }

    /**
     * Extract target tables (INSERT, CREATE statements)
     */
    private fun extractTargetTables(sql: String): List<String> {
        val tables = mutableSetOf<String>()

        // Extract INSERT INTO tables
        val insertMatcher = INSERT_PATTERN.matcher(sql)
        while (insertMatcher.find()) {
            val tableName = insertMatcher.group(1).trim('`', '"', ' ')
            if (isValidTableName(tableName)) {
                tables.add(normalizeTableName(tableName))
            }
        }

        // Extract CREATE TABLE statements
        val createMatcher = CREATE_TABLE_PATTERN.matcher(sql)
        while (createMatcher.find()) {
            val tableName = createMatcher.group(1).trim('`', '"', ' ')
            if (isValidTableName(tableName)) {
                tables.add(normalizeTableName(tableName))
            }
        }

        return tables.toList().sorted()
    }

    /**
     * Extract CTE (WITH clause) names
     */
    private fun extractCTENames(sql: String): List<String> {
        val cteNames = mutableListOf<String>()
        val withMatcher = WITH_PATTERN.matcher(sql)
        while (withMatcher.find()) {
            cteNames.add(withMatcher.group(1))
        }
        return cteNames
    }

    /**
     * Enhance sources with mock dependencies for realistic demos
     */
    private fun enhanceWithMockDependencies(tables: List<String>): List<String> {
        val enhanced = mutableSetOf<String>()
        enhanced.addAll(tables)

        tables.forEach { table ->
            MOCK_TABLE_MAPPINGS[table]?.let { dependencies ->
                enhanced.addAll(dependencies)
            }
        }

        return enhanced.toList().sorted()
    }

    /**
     * Extract mock column lineage for demo purposes
     */
    private fun extractMockColumnLineage(
        sql: String,
        sourceTables: List<String>,
        targetTables: List<String>,
    ): Map<String, List<String>> {
        if (targetTables.isEmpty() || sourceTables.isEmpty()) {
            return emptyMap()
        }

        // Mock column lineage - in real implementation would parse SELECT columns
        val mockColumnLineage = mutableMapOf<String, List<String>>()

        if (sql.contains("user", ignoreCase = true)) {
            mockColumnLineage["user_id"] = listOf("source.user_id", "profile.id")
            mockColumnLineage["email"] = listOf("source.email_address")
        }

        if (sql.contains("order", ignoreCase = true)) {
            mockColumnLineage["order_id"] = listOf("source.order_id")
            mockColumnLineage["amount"] = listOf("source.total_amount", "payment.amount")
        }

        return mockColumnLineage
    }

    /**
     * Validate table name format
     */
    private fun isValidTableName(tableName: String): Boolean {
        if (tableName.isBlank()) return false

        // Exclude subqueries and function calls
        if (tableName.contains("(") || tableName.contains(")")) return false

        // Must contain at least project.dataset.table or dataset.table format
        val parts = tableName.split(".")
        return parts.size >= 2 && parts.all { it.isNotEmpty() }
    }

    /**
     * Normalize table name (remove quotes, lowercase)
     */
    private fun normalizeTableName(tableName: String): String = tableName.trim('`', '"', ' ').lowercase()
}
