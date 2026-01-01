package com.github.lambda.domain.command.metric

/**
 * Command to create a new metric
 */
data class CreateMetricCommand(
    val name: String,
    val owner: String,
    val sql: String,
    val description: String? = null,
    val team: String? = null,
    val sourceTable: String? = null,
    val tags: Set<String> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
        require(name.matches(Regex("^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\$"))) {
            "Metric name must follow pattern: catalog.schema.name"
        }
        require(owner.isNotBlank()) { "Owner cannot be blank" }
        require(sql.isNotBlank()) { "SQL cannot be blank" }
        description?.let {
            require(it.length <= 1000) { "Description must not exceed 1000 characters" }
        }
    }
}

/**
 * Command to update an existing metric
 */
data class UpdateMetricCommand(
    val name: String,
    val sql: String? = null,
    val description: String? = null,
    val team: String? = null,
    val sourceTable: String? = null,
    val tags: Set<String>? = null,
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
        sql?.let { require(it.isNotBlank()) { "SQL cannot be blank if provided" } }
        description?.let {
            require(it.length <= 1000) { "Description must not exceed 1000 characters" }
        }
    }
}

/**
 * Command to delete a metric (soft delete)
 */
data class DeleteMetricCommand(
    val name: String,
    val deletedBy: String,
    val reason: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
        require(deletedBy.isNotBlank()) { "DeletedBy cannot be blank" }
    }
}

/**
 * Command to execute a metric SQL
 */
data class ExecuteMetricCommand(
    val name: String,
    val parameters: Map<String, Any> = emptyMap(),
    val limit: Int = 100,
    val dryRun: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
        require(limit in 1..10000) { "Limit must be between 1 and 10000" }
    }
}
