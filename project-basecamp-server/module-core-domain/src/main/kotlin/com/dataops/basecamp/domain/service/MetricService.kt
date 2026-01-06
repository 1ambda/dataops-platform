package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.MetricAlreadyExistsException
import com.dataops.basecamp.common.exception.MetricNotFoundException
import com.dataops.basecamp.domain.entity.metric.MetricEntity
import com.dataops.basecamp.domain.projection.metric.MetricExecutionProjection
import com.dataops.basecamp.domain.repository.metric.MetricRepositoryDsl
import com.dataops.basecamp.domain.repository.metric.MetricRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.system.measureTimeMillis

/**
 * Metric Service
 *
 * Handles metric CRUD operations.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 */
@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,
    private val metricRepositoryDsl: MetricRepositoryDsl,
) {
    /**
     * Create a new metric
     *
     * @param name Fully qualified metric name (catalog.schema.name)
     * @param owner Owner email address
     * @param team Team name (optional)
     * @param description Metric description (optional)
     * @param sql SQL expression
     * @param sourceTable Source table name (optional)
     * @param tags List of tags (optional)
     * @return Created metric entity
     * @throws MetricAlreadyExistsException if metric with same name exists
     */
    @Transactional
    fun createMetric(
        name: String,
        owner: String,
        team: String? = null,
        description: String? = null,
        sql: String,
        sourceTable: String? = null,
        tags: List<String> = emptyList(),
    ): MetricEntity {
        // Check for duplicates
        if (metricRepositoryJpa.existsByName(name)) {
            throw MetricAlreadyExistsException(name)
        }

        val entity =
            MetricEntity(
                name = name,
                owner = owner,
                team = team,
                description = description,
                sql = sql,
                sourceTable = sourceTable,
                tags = tags.toMutableSet(),
                dependencies = extractDependencies(sql).toMutableSet(),
            )

        return metricRepositoryJpa.save(entity)
    }

    /**
     * Get metric by name
     *
     * @param name Fully qualified metric name
     * @return Metric entity or null if not found
     */
    fun getMetric(name: String): MetricEntity? = metricRepositoryJpa.findByName(name)?.takeIf { !it.isDeleted }

    /**
     * Get metric by name (throws exception if not found)
     *
     * @param name Fully qualified metric name
     * @return Metric entity
     * @throws MetricNotFoundException if metric not found
     */
    fun getMetricOrThrow(name: String): MetricEntity = getMetric(name) ?: throw MetricNotFoundException(name)

    /**
     * Get metric by id
     *
     * @param id Metric id
     * @return Metric entity or null if not found
     */
    fun getMetricById(id: Long): MetricEntity? = metricRepositoryJpa.findById(id).orElse(null)?.takeIf { !it.isDeleted }

    /**
     * List metrics with filters
     *
     * @param tag Filter by tag (exact match)
     * @param owner Filter by owner (partial match)
     * @param search Search in name and description
     * @param limit Maximum results (1-500)
     * @param offset Pagination offset
     * @return List of matching metrics
     */
    fun listMetrics(
        tag: String? = null,
        owner: String? = null,
        search: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<MetricEntity> = metricRepositoryDsl.findByFilters(tag, owner, search, limit, offset)

    /**
     * Update metric
     *
     * @param name Metric name
     * @param description New description (optional)
     * @param sql New SQL expression (optional)
     * @param sourceTable New source table (optional)
     * @param tags New tags (optional)
     * @return Updated metric entity
     * @throws MetricNotFoundException if metric not found
     */
    @Transactional
    fun updateMetric(
        name: String,
        description: String? = null,
        sql: String? = null,
        sourceTable: String? = null,
        tags: List<String>? = null,
    ): MetricEntity {
        val entity = getMetricOrThrow(name)

        description?.let { entity.description = it }
        sql?.let {
            entity.sql = it
            entity.updateDependencies(extractDependencies(it))
        }
        sourceTable?.let { entity.sourceTable = it }
        tags?.let { entity.updateTags(it.toSet()) }

        return metricRepositoryJpa.save(entity)
    }

    /**
     * Delete metric (soft delete)
     *
     * @param name Metric name
     * @return true if deleted successfully
     * @throws MetricNotFoundException if metric not found
     */
    @Transactional
    fun deleteMetric(name: String): Boolean {
        val entity = getMetricOrThrow(name)
        entity.deletedAt = java.time.LocalDateTime.now()
        metricRepositoryJpa.save(entity)
        return true
    }

    // === Metric Execution (MetricExecutionService 통합) ===

    /**
     * Execute a metric with parameters
     *
     * @param metricName Fully qualified metric name
     * @param parameters SQL template parameters
     * @param limit Maximum rows to return (optional)
     * @param timeout Execution timeout in seconds (default: 300)
     * @return Execution result with rows and metadata
     * @throws MetricNotFoundException if metric not found
     * @throws MetricExecutionException if execution fails
     */
    fun executeMetric(
        metricName: String,
        parameters: Map<String, Any> = emptyMap(),
        limit: Int? = null,
        timeout: Int = 300,
    ): MetricExecutionProjection {
        val metric = getMetricOrThrow(metricName)
        val renderedSql = renderSqlWithParameters(metric.sql, parameters)

        // Mock execution - returns sample data
        val (rows, durationMs) = mockExecute(renderedSql, limit)

        return MetricExecutionProjection(
            rows = rows,
            rowCount = rows.size,
            durationSeconds = durationMs / 1000.0,
            renderedSql = renderedSql,
        )
    }

    /**
     * Dry run - render SQL without executing
     *
     * @param metricName Fully qualified metric name
     * @param parameters SQL template parameters
     * @return Rendered SQL
     */
    fun dryRun(
        metricName: String,
        parameters: Map<String, Any> = emptyMap(),
    ): String {
        val metric = getMetricOrThrow(metricName)
        return renderSqlWithParameters(metric.sql, parameters)
    }

    // === Private Methods ===

    /**
     * Extract table dependencies from SQL
     *
     * Parses FROM and JOIN clauses to find referenced tables.
     */
    private fun extractDependencies(sql: String): Set<String> {
        val regex = Regex("(?:FROM|JOIN)\\s+([\\w.]+)", RegexOption.IGNORE_CASE)
        return regex
            .findAll(sql)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Render SQL with parameters
     *
     * Replaces {{param}} placeholders with actual values.
     * Strings are quoted, other types are converted to string.
     */
    private fun renderSqlWithParameters(
        sql: String,
        parameters: Map<String, Any>,
    ): String {
        var result = sql
        parameters.forEach { (key, value) ->
            val placeholder = "{{$key}}"
            val replacement =
                when (value) {
                    is String -> "'${value.replace("'", "''")}'"
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    else -> "'${value.toString().replace("'", "''")}'"
                }
            result = result.replace(placeholder, replacement)
        }
        return result
    }

    /**
     * Mock execution - returns sample data
     *
     * TODO: Replace with actual query engine integration
     */
    private fun mockExecute(
        sql: String,
        limit: Int?,
    ): Pair<List<Map<String, Any>>, Long> {
        val rows = mutableListOf<Map<String, Any>>()
        val durationMs =
            measureTimeMillis {
                // Generate mock data based on the SQL
                val mockRowCount = limit?.coerceIn(1, 100) ?: 10

                for (i in 1..mockRowCount) {
                    rows.add(
                        mapOf(
                            "id" to i,
                            "value" to (i * 100.0),
                            "name" to "sample_$i",
                            "timestamp" to
                                java.time.LocalDateTime
                                    .now()
                                    .toString(),
                        ),
                    )
                }

                // Simulate some processing time
                Thread.sleep(50)
            }

        return Pair(rows, durationMs)
    }
}
