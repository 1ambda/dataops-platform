package com.github.lambda.domain.service

import com.github.lambda.common.exception.MetricAlreadyExistsException
import com.github.lambda.common.exception.MetricNotFoundException
import com.github.lambda.domain.entity.metric.MetricEntity
import com.github.lambda.domain.repository.metric.MetricRepositoryDsl
import com.github.lambda.domain.repository.metric.MetricRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
}
