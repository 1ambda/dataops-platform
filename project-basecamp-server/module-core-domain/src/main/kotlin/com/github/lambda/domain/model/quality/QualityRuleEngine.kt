package com.github.lambda.domain.model.quality

/**
 * Request model for generating Quality Test SQL via external rule engine
 *
 * @param testType The type of quality test to generate SQL for
 * @param resourceName Fully qualified resource name (e.g., "iceberg.analytics.users")
 * @param column Column name for column-level tests (optional for table-level tests)
 * @param config Test-specific configuration parameters
 */
data class GenerateSqlRequest(
    val testType: TestType,
    val resourceName: String,
    val column: String? = null,
    val config: Map<String, Any> = emptyMap(),
)

/**
 * Response model for generated Quality Test SQL
 *
 * @param sql Main SQL query for executing the quality test
 * @param sampleFailuresSql Optional SQL query for retrieving sample failing records
 */
data class GenerateSqlResponse(
    val sql: String,
    val sampleFailuresSql: String? = null,
)

/**
 * Configuration for accepted values test
 */
data class AcceptedValuesConfig(
    val values: List<String>,
)

/**
 * Configuration for relationships test
 */
data class RelationshipsConfig(
    val toTable: String,
    val toColumn: String,
)

/**
 * Configuration for expression test
 */
data class ExpressionConfig(
    val expression: String,
    val description: String? = null,
)

/**
 * Configuration for row count test
 */
data class RowCountConfig(
    val min: Long? = null,
    val max: Long? = null,
)
