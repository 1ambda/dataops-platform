package com.github.lambda.domain.repository

import com.github.lambda.domain.entity.metric.MetricEntity

/**
 * Metric Repository DSL Interface (Pure Domain Abstraction)
 *
 * Defines complex query operations for metrics.
 * Provides abstraction for QueryDSL or other complex query technologies.
 */
interface MetricRepositoryDsl {
    /**
     * Find metrics with filters
     *
     * @param tag Filter by tag (exact match in tags collection)
     * @param owner Filter by owner (partial match, case-insensitive)
     * @param search Search in name and description (partial match, case-insensitive)
     * @param limit Maximum number of results (1-500)
     * @param offset Pagination offset
     * @return List of matching metrics ordered by updatedAt DESC
     */
    fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricEntity>

    /**
     * Find metrics by tags
     */
    fun findByTagsIn(tags: List<String>): List<MetricEntity>
}
