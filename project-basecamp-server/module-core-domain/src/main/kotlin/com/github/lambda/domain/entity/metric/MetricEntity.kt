package com.github.lambda.domain.entity.metric

import com.github.lambda.domain.entity.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Metric Entity
 *
 * SQL-based calculation that can be registered, queried, and executed on-demand.
 * Uses fully qualified name format: catalog.schema.name
 */
@Entity
@Table(
    name = "metrics",
    indexes = [
        Index(name = "idx_metrics_name", columnList = "name", unique = true),
        Index(name = "idx_metrics_owner", columnList = "owner"),
        Index(name = "idx_metrics_updated_at", columnList = "updated_at"),
    ],
)
class MetricEntity(
    @NotBlank(message = "Metric name is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+$",
        message = "Name must follow pattern: catalog.schema.name",
    )
    @Column(name = "name", nullable = false, unique = true, length = 255)
    var name: String = "",
    @NotBlank(message = "Owner is required")
    @Email(message = "Owner must be a valid email")
    @Column(name = "owner", nullable = false, length = 100)
    var owner: String = "",
    @Size(max = 100, message = "Team must not exceed 100 characters")
    @Column(name = "team", length = 100)
    var team: String? = null,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @NotBlank(message = "SQL is required")
    @Column(name = "sql_expression", nullable = false, columnDefinition = "TEXT")
    var sql: String = "",
    @Column(name = "source_table", length = 255)
    var sourceTable: String? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "metric_tags",
        joinColumns = [JoinColumn(name = "metric_id")],
    )
    @Column(name = "tag", length = 50)
    var tags: MutableSet<String> = mutableSetOf(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "metric_dependencies",
        joinColumns = [JoinColumn(name = "metric_id")],
    )
    @Column(name = "dependency", length = 255)
    var dependencies: MutableSet<String> = mutableSetOf(),
) : BaseEntity() {
    /**
     * Update metric tags
     */
    fun updateTags(newTags: Set<String>) {
        tags.clear()
        tags.addAll(newTags)
    }

    /**
     * Update metric dependencies
     */
    fun updateDependencies(newDependencies: Set<String>) {
        dependencies.clear()
        dependencies.addAll(newDependencies)
    }

    /**
     * Extract catalog from fully qualified name
     */
    fun getCatalog(): String = name.split(".").getOrNull(0) ?: ""

    /**
     * Extract schema from fully qualified name
     */
    fun getSchema(): String = name.split(".").getOrNull(1) ?: ""

    /**
     * Extract metric name from fully qualified name
     */
    fun getMetricName(): String = name.split(".").getOrNull(2) ?: name
}
