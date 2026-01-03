package com.github.lambda.domain.model.transpile

/**
 * SQL Dialect Enumeration
 *
 * Supported SQL dialects for transpile operations.
 * Used to specify source and target dialects in SQL transformation.
 */
enum class SqlDialect {
    /**
     * Trino (formerly PrestoSQL) dialect
     */
    TRINO,

    /**
     * Google BigQuery dialect
     */
    BIGQUERY,

    /**
     * Universal dialect matcher (matches any dialect)
     */
    ANY,
}
