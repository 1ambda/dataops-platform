package com.dataops.basecamp.domain.fixtures

import com.dataops.basecamp.domain.entity.metric.MetricEntity
import java.time.LocalDateTime

/**
 * Test fixtures for Metric-related tests.
 *
 * Provides factory methods for creating consistent test data.
 */
object MetricTestFixtures {
    /**
     * Create a basic metric entity for testing.
     */
    fun createMetricEntity(
        name: String = "test_catalog.test_schema.test_metric",
        owner: String = "test@example.com",
        team: String? = "data-team",
        description: String? = "Test metric description",
        sql: String = "SELECT COUNT(*) as count FROM users",
        sourceTable: String? = "users",
        tags: Set<String> = setOf("test", "user"),
        dependencies: Set<String> = setOf("users"),
    ): MetricEntity =
        MetricEntity(
            name = name,
            owner = owner,
            team = team,
            description = description,
            sql = sql,
            sourceTable = sourceTable,
            tags = tags.toMutableSet(),
            dependencies = dependencies.toMutableSet(),
        )

    /**
     * Create a metric entity with complex SQL for dependency extraction testing.
     */
    fun createMetricWithComplexSql(
        name: String = "analytics.reports.complex_metric",
        owner: String = "analytics@example.com",
    ): MetricEntity {
        val sql =
            """
            SELECT
                u.id,
                u.name,
                COUNT(o.id) as order_count,
                SUM(o.amount) as total_amount
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            LEFT JOIN payments p ON o.id = p.order_id
            WHERE u.created_at > '{{start_date}}'
            GROUP BY u.id, u.name
            """.trimIndent()

        return createMetricEntity(
            name = name,
            owner = owner,
            sql = sql,
            dependencies = setOf("users", "orders", "payments"),
        )
    }

    /**
     * Create a metric entity with parameters in SQL.
     */
    fun createMetricWithParameters(
        name: String = "finance.monthly.revenue",
        owner: String = "finance@example.com",
    ): MetricEntity {
        val sql =
            """
            SELECT
                DATE_TRUNC('month', created_at) as month,
                SUM(amount) as revenue
            FROM transactions
            WHERE created_at BETWEEN '{{start_date}}' AND '{{end_date}}'
                AND status = '{{status}}'
            GROUP BY 1
            ORDER BY 1
            """.trimIndent()

        return createMetricEntity(
            name = name,
            owner = owner,
            sql = sql,
            description = "Monthly revenue metric",
            tags = setOf("finance", "revenue", "monthly"),
            dependencies = setOf("transactions"),
        )
    }

    /**
     * Create a deleted (soft-deleted) metric entity.
     */
    fun createDeletedMetricEntity(
        name: String = "archived.legacy.old_metric",
        owner: String = "old@example.com",
    ): MetricEntity {
        val metric = createMetricEntity(name = name, owner = owner)
        metric.deletedAt = LocalDateTime.now().minusDays(1)
        return metric
    }

    /**
     * Create a list of test metrics for pagination testing.
     */
    fun createMetricList(count: Int = 10): List<MetricEntity> =
        (1..count).map { i ->
            createMetricEntity(
                name = "catalog$i.schema$i.metric_$i",
                owner = "owner$i@example.com",
                team = "team-$i",
                description = "Test metric $i description",
                tags = setOf("tag$i", "common"),
            )
        }

    /**
     * Create metrics for different owners.
     */
    fun createMetricsForOwners(vararg owners: String): List<MetricEntity> =
        owners.mapIndexed { index, owner ->
            createMetricEntity(
                name = "catalog.schema.metric_${index + 1}",
                owner = owner,
            )
        }

    /**
     * Create metrics with specific tags for filtering tests.
     */
    fun createMetricsWithTags(tagGroups: Map<String, Set<String>>): List<MetricEntity> =
        tagGroups.entries.mapIndexed { index, (name, tags) ->
            createMetricEntity(
                name = "catalog.schema.$name",
                owner = "test@example.com",
                tags = tags,
            )
        }
}
