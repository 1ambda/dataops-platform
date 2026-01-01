package com.github.lambda.domain.query.metric

/**
 * Query to get a single metric by name
 */
data class GetMetricQuery(
    val name: String,
    val includeDeleted: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
    }
}

/**
 * Query to list metrics with optional filters
 */
data class GetMetricsQuery(
    val tag: String? = null,
    val owner: String? = null,
    val search: String? = null,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    init {
        require(limit in 1..500) { "Limit must be between 1 and 500" }
        require(offset >= 0) { "Offset must be non-negative" }
    }
}

/**
 * Query to render SQL with parameters
 */
data class RenderSqlQuery(
    val name: String,
    val parameters: Map<String, Any> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
    }
}
