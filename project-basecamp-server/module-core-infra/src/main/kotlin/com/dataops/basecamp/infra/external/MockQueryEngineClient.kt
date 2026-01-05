package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.exception.InvalidSqlException
import com.dataops.basecamp.common.exception.QueryExecutionTimeoutException
import com.dataops.basecamp.domain.external.queryengine.QueryEngineClient
import com.dataops.basecamp.domain.external.queryengine.QueryExecutionResponse
import com.dataops.basecamp.domain.external.queryengine.QueryValidationResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID
import kotlin.random.Random

/**
 * Mock Query Engine Client Implementation
 *
 * Provides mock query execution responses for development and testing
 * while the actual BigQuery/Trino integration is not yet implemented.
 *
 * Testability: When deterministicMode=true, all random behaviors are disabled:
 * - Fixed delay (100ms)
 * - Deterministic value generation based on index
 * - No sleep in simulateDelay (instant return)
 *
 * @param deterministicMode When true, disables random behaviors for deterministic testing.
 *                          Default is false for realistic simulation in development.
 */
@Repository("queryEngineClient")
class MockQueryEngineClient(
    private val deterministicMode: Boolean = false,
) : QueryEngineClient {
    private val log = LoggerFactory.getLogger(MockQueryEngineClient::class.java)

    private val supportedEngines = listOf("bigquery", "trino")

    // Seeded random for reproducible results when not in deterministic mode
    private val seededRandom = Random(42)

    // Common SQL keywords for basic validation
    private val selectKeywords = listOf("SELECT", "WITH")
    private val invalidPatterns =
        listOf(
            "SELET" to "SELECT",
            "FORM" to "FROM",
            "WEHRE" to "WHERE",
            "GRUOP" to "GROUP",
            "ODRER" to "ORDER",
        )

    override fun execute(
        sql: String,
        engine: String,
        timeoutSeconds: Int,
        maxRows: Int,
    ): QueryExecutionResponse {
        log.info(
            "Mock Query Engine: Executing SQL - engine: {}, timeoutSeconds: {}, maxRows: {}",
            engine,
            timeoutSeconds,
            maxRows,
        )
        log.debug("SQL: {}", sql.take(500))

        // Validate SQL first
        val validation = validateSQL(sql, engine)
        if (!validation.valid) {
            throw InvalidSqlException(
                sql = sql,
                sqlError = validation.errorMessage ?: "Invalid SQL",
            )
        }

        // Simulate execution delay (100-500ms) - shorter in deterministic mode
        val delay = if (deterministicMode) 100L else Random.nextLong(100, 500)
        simulateDelay(delay)

        // Simulate timeout for very long queries (if SQL contains "SLEEP" or timeout trigger)
        if (sql.uppercase().contains("SLEEP") || sql.uppercase().contains("TRIGGER_TIMEOUT")) {
            throw QueryExecutionTimeoutException(
                queryId = "mock_${UUID.randomUUID().toString().take(8)}",
                timeoutSeconds = timeoutSeconds,
            )
        }

        // Generate mock results based on query type
        val rows = generateMockResults(sql, maxRows, engine)
        val bytesScanned = calculateMockBytesScanned(sql, rows.size)
        val costUsd = calculateMockCost(bytesScanned, engine)

        log.info(
            "Mock Query Engine: Execution complete - rows: {}, bytesScanned: {}, cost: {}",
            rows.size,
            formatBytes(bytesScanned),
            costUsd,
        )

        return QueryExecutionResponse(
            rows = rows,
            bytesScanned = bytesScanned,
            costUsd = costUsd,
            executionTimeSeconds = delay / 1000.0,
            totalRows = rows.size.toLong(),
            warnings = generateWarnings(sql),
        )
    }

    override fun validateSQL(
        sql: String,
        engine: String,
    ): QueryValidationResponse {
        log.debug("Mock Query Engine: Validating SQL for engine: {}", engine)
        val startTime = System.currentTimeMillis()

        // Check if SQL is empty
        val trimmedSql = sql.trim()
        if (trimmedSql.isEmpty()) {
            return QueryValidationResponse(
                valid = false,
                errorMessage = "SQL query cannot be empty",
                validationTimeSeconds = 0.001,
            )
        }

        // Check for common typos
        for ((typo, correct) in invalidPatterns) {
            if (trimmedSql.uppercase().contains(typo)) {
                return QueryValidationResponse(
                    valid = false,
                    errorMessage = "SQL syntax error: Unrecognized keyword '$typo'",
                    errorDetails =
                        mapOf(
                            "suggestion" to "Did you mean '$correct'?",
                            "position" to trimmedSql.uppercase().indexOf(typo),
                        ),
                    validationTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0,
                )
            }
        }

        // Check if SQL starts with valid keyword
        val upperSql = trimmedSql.uppercase()
        val startsWithValidKeyword = selectKeywords.any { upperSql.startsWith(it) }
        if (!startsWithValidKeyword) {
            return QueryValidationResponse(
                valid = false,
                errorMessage = "SQL must start with SELECT or WITH clause",
                validationTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0,
            )
        }

        // Check for balanced parentheses
        val openParens = trimmedSql.count { it == '(' }
        val closeParens = trimmedSql.count { it == ')' }
        if (openParens != closeParens) {
            return QueryValidationResponse(
                valid = false,
                errorMessage = "Unbalanced parentheses in SQL query",
                errorDetails =
                    mapOf(
                        "openParentheses" to openParens,
                        "closeParentheses" to closeParens,
                    ),
                validationTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0,
            )
        }

        val validationTime = (System.currentTimeMillis() - startTime) / 1000.0

        return QueryValidationResponse(
            valid = true,
            validationTimeSeconds = validationTime,
            warnings = generateWarnings(sql),
        )
    }

    override fun getSupportedEngines(): List<String> = supportedEngines

    // === Helper Methods ===

    /**
     * Generate mock result rows based on SQL query
     */
    private fun generateMockResults(
        sql: String,
        maxRows: Int,
        engine: String,
    ): List<Map<String, Any?>> {
        val upperSql = sql.uppercase()

        // Determine number of rows to return
        val rowCount =
            when {
                upperSql.contains("LIMIT 0") -> 0
                upperSql.contains("LIMIT 1") -> 1
                upperSql.contains("LIMIT") -> {
                    val limitMatch = Regex("LIMIT\\s+(\\d+)").find(upperSql)
                    minOf(limitMatch?.groupValues?.get(1)?.toIntOrNull() ?: 100, maxRows)
                }
                else -> minOf(100, maxRows)
            }

        if (rowCount == 0) return emptyList()

        // Determine column structure based on query
        val columns = extractColumnsFromSelect(sql)

        return (1..rowCount).map { index ->
            columns.associate { column ->
                column to generateMockValue(column, index, engine)
            }
        }
    }

    /**
     * Extract column names from SELECT clause
     */
    private fun extractColumnsFromSelect(sql: String): List<String> {
        val upperSql = sql.uppercase()
        val selectIndex = upperSql.indexOf("SELECT")
        val fromIndex = upperSql.indexOf("FROM")

        if (selectIndex == -1 || fromIndex == -1 || fromIndex <= selectIndex) {
            return listOf("column1", "column2", "column3")
        }

        val selectClause = sql.substring(selectIndex + 6, fromIndex).trim()

        // Handle SELECT *
        if (selectClause.trim() == "*") {
            return listOf("id", "name", "value", "created_at")
        }

        // Parse column names
        return selectClause
            .split(",")
            .map { it.trim() }
            .map { col ->
                // Handle aliases: "column AS alias" or "column alias"
                val asMatch = Regex("(?i)\\s+AS\\s+(\\w+)$").find(col)
                val spaceMatch = Regex("\\s+(\\w+)$").find(col)
                when {
                    asMatch != null -> asMatch.groupValues[1]
                    spaceMatch != null && !col.contains("(") -> spaceMatch.groupValues[1]
                    else -> col.split(".").last().replace(Regex("[^a-zA-Z0-9_]"), "")
                }
            }.filter { it.isNotBlank() }
            .take(20) // Limit to 20 columns
    }

    /**
     * Generate mock value based on column name pattern
     *
     * When deterministicMode=true, uses deterministic values based on index.
     * When deterministicMode=false, uses random values for realistic simulation.
     */
    private fun generateMockValue(
        column: String,
        index: Int,
        engine: String,
    ): Any? {
        val lowerColumn = column.lowercase()
        return when {
            lowerColumn.contains("id") -> index.toLong()
            lowerColumn.contains("name") -> "name_$index"
            lowerColumn.contains("email") -> "user$index@example.com"
            lowerColumn.contains("date") || lowerColumn.contains("_at") ->
                "2026-01-0${(index % 9) + 1}"
            lowerColumn.contains("count") || lowerColumn.contains("total") ->
                if (deterministicMode) index * 100 else seededRandom.nextInt(1, 10000)
            lowerColumn.contains("amount") || lowerColumn.contains("price") ->
                if (deterministicMode) {
                    BigDecimal("${index * 10}.00")
                } else {
                    seededRandom
                        .nextDouble(10.0, 1000.0)
                        .toBigDecimal()
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                }
            lowerColumn.contains("active") || lowerColumn.contains("enabled") ->
                if (deterministicMode) (index % 2 == 0) else seededRandom.nextBoolean()
            lowerColumn.contains("status") ->
                listOf("ACTIVE", "PENDING", "COMPLETED")[index % 3]
            lowerColumn.contains("region") ->
                listOf("US", "EU", "APAC")[index % 3]
            else -> "value_$index"
        }
    }

    /**
     * Calculate mock bytes scanned based on query complexity
     */
    private fun calculateMockBytesScanned(
        sql: String,
        rowCount: Int,
    ): Long {
        val baseBytes = 1_000_000L // 1 MB base
        val tableCount = Regex("(?i)FROM|JOIN").findAll(sql).count()
        val complexity = sql.length / 100

        return baseBytes * tableCount * (1 + complexity) + rowCount * 1000L
    }

    /**
     * Calculate mock cost based on bytes scanned and engine
     */
    private fun calculateMockCost(
        bytesScanned: Long,
        engine: String,
    ): BigDecimal {
        // BigQuery: $5 per TB scanned
        // Trino: $0 (on-premises)
        val costPerTb =
            when (engine.lowercase()) {
                "bigquery" -> BigDecimal("5.0")
                "trino" -> BigDecimal.ZERO
                else -> BigDecimal("2.5")
            }

        val tbScanned =
            BigDecimal(bytesScanned).divide(
                BigDecimal("1099511627776"), // 1 TB in bytes
                10,
                java.math.RoundingMode.HALF_UP,
            )

        return tbScanned.multiply(costPerTb).setScale(6, java.math.RoundingMode.HALF_UP)
    }

    /**
     * Generate warnings based on SQL patterns
     */
    private fun generateWarnings(sql: String): List<String> {
        val warnings = mutableListOf<String>()
        val upperSql = sql.uppercase()

        if (!upperSql.contains("LIMIT")) {
            warnings.add("Query has no LIMIT clause - consider adding one for large tables")
        }
        if (upperSql.contains("SELECT *")) {
            warnings.add("SELECT * may return more columns than needed")
        }
        if (upperSql.contains("CROSS JOIN")) {
            warnings.add("CROSS JOIN can produce very large result sets")
        }
        if (upperSql.length > 10000) {
            warnings.add("Query is very long - consider breaking into smaller queries")
        }

        return warnings
    }

    /**
     * Format bytes for logging
     */
    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

    /**
     * Simulate processing delay
     *
     * When deterministicMode=true, no actual sleep occurs (instant return).
     * This significantly speeds up tests.
     */
    private fun simulateDelay(millis: Long) {
        if (deterministicMode) {
            // Skip sleep in deterministic mode for fast tests
            return
        }
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
