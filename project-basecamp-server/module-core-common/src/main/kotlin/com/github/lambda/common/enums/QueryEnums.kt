package com.github.lambda.common.enums

/**
 * Query execution status
 */
enum class QueryStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/**
 * Supported query engines
 */
enum class QueryEngine {
    BIGQUERY,
    TRINO,
    SPARK,
}

/**
 * Query scope for access control
 */
enum class QueryScope {
    MY, // Current user's queries only
    SYSTEM, // System-generated queries
    USER, // Specific user's queries (requires role)
    ALL, // All queries (requires role)
}
