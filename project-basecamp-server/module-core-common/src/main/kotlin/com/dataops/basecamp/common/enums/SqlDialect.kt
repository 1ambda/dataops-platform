package com.dataops.basecamp.common.enums

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
     * Apache Spark SQL dialect
     */
    SPARK,

    /**
     * DuckDB dialect
     */
    DUCKDB,

    /**
     * MySQL dialect
     */
    MYSQL,

    /**
     * PostgreSQL dialect
     */
    POSTGRESQL,

    /**
     * Universal dialect matcher (matches any dialect)
     */
    ANY,
}
