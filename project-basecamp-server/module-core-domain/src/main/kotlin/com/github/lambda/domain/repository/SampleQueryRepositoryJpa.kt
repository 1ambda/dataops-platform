package com.github.lambda.domain.repository

import com.github.lambda.domain.model.catalog.SampleQueryEntity
import org.springframework.data.domain.Pageable

/**
 * Sample Query Repository JPA Interface (Domain Port)
 *
 * Defines CRUD operations for SampleQueryEntity.
 * This is separate from SampleQueryRepositoryDsl which uses domain models.
 */
interface SampleQueryRepositoryJpa {
    // === Basic CRUD Operations ===

    /**
     * Save a sample query entity
     */
    fun save(sampleQuery: SampleQueryEntity): SampleQueryEntity

    /**
     * Find sample query by ID
     */
    fun findById(id: Long): SampleQueryEntity?

    /**
     * Delete sample query by ID
     */
    fun deleteById(id: Long)

    // === Table Reference Based Queries ===

    /**
     * Find sample queries by table reference
     */
    fun findByTableRef(tableRef: String): List<SampleQueryEntity>

    /**
     * Find sample queries by table reference ordered by run count
     */
    fun findByTableRefOrderByRunCountDesc(
        tableRef: String,
        pageable: Pageable,
    ): List<SampleQueryEntity>

    /**
     * Find sample query by table ref and title
     */
    fun findByTableRefAndTitle(
        tableRef: String,
        title: String,
    ): SampleQueryEntity?

    /**
     * Check if sample query exists
     */
    fun existsByTableRefAndTitle(
        tableRef: String,
        title: String,
    ): Boolean

    // === Author Based Queries ===

    /**
     * Find sample queries by author
     */
    fun findByAuthor(author: String): List<SampleQueryEntity>

    // === Statistics ===

    /**
     * Count sample queries for a table
     */
    fun countByTableRef(tableRef: String): Long
}
