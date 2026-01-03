package com.github.lambda.domain.repository

import com.github.lambda.domain.model.catalog.ColumnInfo
import com.github.lambda.domain.model.catalog.TableDetail

/**
 * Catalog Repository JPA Interface (Domain Port)
 *
 * Defines simple CRUD operations for fetching table metadata from external catalog systems.
 * Follows Pure Hexagonal Architecture naming conventions.
 *
 * Operations:
 * - Simple entity lookups by ID/reference
 * - Direct column metadata retrieval
 */
interface CatalogRepositoryJpa {
    /**
     * Get detailed table information
     *
     * @param tableRef Fully qualified table reference (project.dataset.table)
     * @return Table detail or null if not found
     */
    fun getTableDetail(tableRef: String): TableDetail?

    /**
     * Get column metadata for a table
     *
     * @param tableRef Fully qualified table reference
     * @return List of column metadata
     */
    fun getTableColumns(tableRef: String): List<ColumnInfo>
}