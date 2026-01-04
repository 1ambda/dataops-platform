package com.github.lambda.domain.repository.catalog

import com.github.lambda.domain.entity.catalog.CatalogTableEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Catalog Table Repository JPA Interface (Domain Port)
 *
 * Defines basic CRUD operations for CatalogTableEntity.
 * Follows the same pattern as DatasetRepositoryJpa for consistency.
 */
interface CatalogTableRepositoryJpa {
    // === Basic CRUD Operations ===

    /**
     * Save a catalog table entity
     */
    fun save(catalogTable: CatalogTableEntity): CatalogTableEntity

    /**
     * Find catalog table by ID
     */
    fun findById(id: Long): CatalogTableEntity?

    /**
     * Find catalog table by fully qualified name (project.dataset.table)
     */
    fun findByName(name: String): CatalogTableEntity?

    /**
     * Check if catalog table exists by name
     */
    fun existsByName(name: String): Boolean

    /**
     * Delete catalog table by name
     */
    fun deleteByName(name: String): Long

    /**
     * Delete catalog table by ID
     */
    fun deleteById(id: Long)

    // === Project/Dataset Based Queries ===

    /**
     * Find all tables in a project
     */
    fun findByProject(project: String): List<CatalogTableEntity>

    /**
     * Find all tables in a project and dataset
     */
    fun findByProjectAndDatasetName(
        project: String,
        datasetName: String,
    ): List<CatalogTableEntity>

    // === Owner Based Queries ===

    /**
     * Find tables by owner
     */
    fun findByOwner(owner: String): List<CatalogTableEntity>

    /**
     * Find tables by owner with pagination
     */
    fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<CatalogTableEntity>

    // === Team Based Queries ===

    /**
     * Find tables by team
     */
    fun findByTeam(team: String): List<CatalogTableEntity>

    // === Tag Based Queries ===

    /**
     * Find tables containing a specific tag
     */
    fun findByTagsContaining(tag: String): List<CatalogTableEntity>

    // === Engine Based Queries ===

    /**
     * Find tables by engine type
     */
    fun findByEngine(engine: String): List<CatalogTableEntity>

    // === Pagination Queries ===

    /**
     * Find all tables with pagination ordered by last updated
     */
    fun findAllByOrderByLastUpdatedDesc(pageable: Pageable): Page<CatalogTableEntity>

    // === Search Queries ===

    /**
     * Find tables by name containing (case insensitive)
     */
    fun findByNameContainingIgnoreCase(namePattern: String): List<CatalogTableEntity>

    /**
     * Find tables by description containing (case insensitive)
     */
    fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<CatalogTableEntity>

    // === Statistics ===

    /**
     * Count tables by project
     */
    fun countByProject(project: String): Long

    /**
     * Count tables by owner
     */
    fun countByOwner(owner: String): Long

    /**
     * Count all tables
     */
    fun count(): Long
}
