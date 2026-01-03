package com.github.lambda.domain.external

/**
 * Basecamp Parser Client Interface (Port)
 *
 * Client interface for connecting to project-basecamp-parser service.
 * Parses SQL queries to extract table dependencies and build lineage graphs.
 * Infrastructure layer provides implementation (either mock or real parser service).
 */
interface BasecampParserClient {
    /**
     * Parse SQL query to extract table dependencies
     *
     * @param sql SQL query to parse
     * @param dialect SQL dialect (bigquery, trino, postgres, etc.)
     * @return Parsing result with source and target tables
     */
    fun parseSQL(
        sql: String,
        dialect: String = "bigquery",
    ): SQLLineageResult

    /**
     * Validate if SQL can be parsed
     *
     * @param sql SQL query to validate
     * @param dialect SQL dialect
     * @return true if SQL can be parsed, false otherwise
     */
    fun validateSQL(
        sql: String,
        dialect: String = "bigquery",
    ): Boolean

    /**
     * Get supported SQL dialects
     */
    fun getSupportedDialects(): List<String>

    /**
     * Check if dialect is supported
     */
    fun isDialectSupported(dialect: String): Boolean = getSupportedDialects().contains(dialect.lowercase())
}

/**
 * SQL Lineage parsing result
 *
 * Contains the parsed dependencies from a SQL query.
 *
 * @param success Whether parsing was successful
 * @param sourceTables Tables read by the SQL (inputs)
 * @param targetTables Tables written by the SQL (outputs) - usually from INSERT/CREATE statements
 * @param columnLineage Optional column-level lineage mapping
 * @param errorMessage Error message if parsing failed
 */
data class SQLLineageResult(
    val success: Boolean,
    val sourceTables: List<String> = emptyList(),
    val targetTables: List<String> = emptyList(),
    val columnLineage: Map<String, List<String>> = emptyMap(), // target_column -> [source_columns]
    val errorMessage: String? = null,
) {
    companion object {
        fun success(
            sourceTables: List<String>,
            targetTables: List<String> = emptyList(),
            columnLineage: Map<String, List<String>> = emptyMap(),
        ) = SQLLineageResult(
            success = true,
            sourceTables = sourceTables,
            targetTables = targetTables,
            columnLineage = columnLineage,
        )

        fun error(message: String) =
            SQLLineageResult(
                success = false,
                errorMessage = message,
            )
    }
}
