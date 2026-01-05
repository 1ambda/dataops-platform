package com.dataops.basecamp.domain.repository.lineage

import com.dataops.basecamp.common.enums.LineageNodeType
import com.dataops.basecamp.domain.entity.lineage.LineageNodeEntity

/**
 * Lineage Node Repository DSL Interface (Pure Domain Abstraction)
 *
 * Defines complex query operations for lineage nodes.
 * Provides abstraction for QueryDSL or other complex query technologies.
 */
interface LineageNodeRepositoryDsl {
    /**
     * Find nodes with filters
     *
     * @param type Filter by node type
     * @param owner Filter by owner (partial match, case-insensitive)
     * @param team Filter by team (partial match, case-insensitive)
     * @param tag Filter by tag (exact match in tags collection)
     * @param search Search in name and description (partial match, case-insensitive)
     * @param limit Maximum number of results (1-500)
     * @param offset Pagination offset
     * @return List of matching nodes ordered by name ASC
     */
    fun findByFilters(
        type: LineageNodeType?,
        owner: String?,
        team: String?,
        tag: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<LineageNodeEntity>

    /**
     * Find nodes by multiple names
     */
    fun findByNamesIn(names: List<String>): List<LineageNodeEntity>

    /**
     * Find nodes by tags
     */
    fun findByTagsIn(tags: List<String>): List<LineageNodeEntity>

    /**
     * Find nodes by name pattern (for partial matching)
     */
    fun findByNameContaining(namePattern: String): List<LineageNodeEntity>
}
