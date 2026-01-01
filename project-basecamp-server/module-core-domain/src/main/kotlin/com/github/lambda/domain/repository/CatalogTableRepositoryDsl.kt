package com.github.lambda.domain.repository

import com.github.lambda.domain.model.catalog.CatalogFilters
import com.github.lambda.domain.model.catalog.CatalogTableEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Catalog Table Repository DSL Interface (Domain Port)
 *
 * Defines complex query operations for CatalogTableEntity.
 * Follows the same pattern as DatasetRepositoryDsl for consistency.
 */
interface CatalogTableRepositoryDsl {
    /**
     * Find tables with complex filtering
     *
     * @param filters Filter criteria (project, dataset, owner, team, tags)
     * @param pageable Pagination information
     * @return Page of matching tables
     */
    fun findByFilters(
        filters: CatalogFilters,
        pageable: Pageable,
    ): Page<CatalogTableEntity>

    /**
     * Count tables matching filters
     *
     * @param filters Filter criteria
     * @return Count of matching tables
     */
    fun countByFilters(filters: CatalogFilters): Long

    /**
     * Search tables by keyword across name, description, and column names
     *
     * @param keyword Search keyword
     * @param project Optional project filter
     * @param limit Maximum results
     * @return List of matching tables with match context
     */
    fun searchByKeyword(
        keyword: String,
        project: String? = null,
        limit: Int = 20,
    ): List<CatalogTableEntity>

    /**
     * Find tables with stale data
     *
     * @param thresholdHours Stale threshold in hours
     * @param limit Maximum results
     * @return List of stale tables
     */
    fun findStaleTables(
        thresholdHours: Int = 24,
        limit: Int = 100,
    ): List<CatalogTableEntity>

    /**
     * Get catalog statistics
     *
     * @param project Optional project filter
     * @return Statistics map
     */
    fun getCatalogStatistics(project: String? = null): Map<String, Any>

    /**
     * Get table count by project
     *
     * @return List of project name to count mappings
     */
    fun getTableCountByProject(): List<Map<String, Any>>

    /**
     * Get table count by owner
     *
     * @return List of owner to count mappings
     */
    fun getTableCountByOwner(): List<Map<String, Any>>

    /**
     * Get table count by tag
     *
     * @return List of tag to count mappings
     */
    fun getTableCountByTag(): List<Map<String, Any>>

    /**
     * Find recently updated tables
     *
     * @param limit Maximum results
     * @param daysSince Days since last update
     * @return List of recently updated tables
     */
    fun findRecentlyUpdatedTables(
        limit: Int,
        daysSince: Int,
    ): List<CatalogTableEntity>

    /**
     * Find tables by quality score range
     *
     * @param minScore Minimum quality score (inclusive)
     * @param maxScore Maximum quality score (inclusive)
     * @param pageable Pagination information
     * @return Page of tables in quality score range
     */
    fun findByQualityScoreRange(
        minScore: Int,
        maxScore: Int,
        pageable: Pageable,
    ): Page<CatalogTableEntity>
}
