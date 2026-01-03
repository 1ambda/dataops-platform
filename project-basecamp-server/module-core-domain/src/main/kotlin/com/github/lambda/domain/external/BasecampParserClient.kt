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
    ): LineageResult

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

    /**
     * Transpile SQL from one dialect to another
     *
     * @param sql SQL query to transpile
     * @param sourceDialect Source SQL dialect
     * @param targetDialect Target SQL dialect
     * @param rules List of custom transformation rules
     * @return Transpilation result with converted SQL
     */
    fun transpileSQL(
        sql: String,
        sourceDialect: String,
        targetDialect: String,
        rules: List<TranspileRule> = emptyList(),
    ): TranspileResult
}
