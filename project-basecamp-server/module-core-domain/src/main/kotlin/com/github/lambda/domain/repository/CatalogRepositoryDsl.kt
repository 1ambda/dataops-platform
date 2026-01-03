package com.github.lambda.domain.repository

import com.github.lambda.domain.model.catalog.CatalogFilters
import com.github.lambda.domain.model.catalog.TableInfo

/**
 * Catalog Repository DSL Interface (Domain Port)
 *
 * Defines complex query operations for fetching table metadata from external catalog systems.
 * Follows Pure Hexagonal Architecture naming conventions.
 *
 * Operations:
 * - Complex filtering and pagination
 * - Text search across metadata
 * - Sample data retrieval
 */
interface CatalogRepositoryDsl {
    /**
     * List tables with optional filters
     *
     * @param filters Filter parameters (project, dataset, owner, team, tags)
     * @return List of table summaries matching the filters
     */
    fun listTables(filters: CatalogFilters): List<TableInfo>

    /**
     * Search tables by keyword
     *
     * @param keyword Search keyword (matches table names, column names, descriptions)
     * @param project Optional project filter
     * @param limit Maximum results to return
     * @return List of matching tables with match context
     */
    fun searchTables(
        keyword: String,
        project: String?,
        limit: Int,
    ): List<TableInfo>

    /**
     * Get sample data from a table
     *
     * @param tableRef Fully qualified table reference
     * @param limit Number of rows to retrieve
     * @return List of sample data rows
     */
    fun getSampleData(
        tableRef: String,
        limit: Int,
    ): List<Map<String, Any>>
}
