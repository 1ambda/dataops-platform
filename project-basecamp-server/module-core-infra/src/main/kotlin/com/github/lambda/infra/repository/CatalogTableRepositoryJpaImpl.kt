package com.github.lambda.infra.repository

import com.github.lambda.domain.model.catalog.CatalogTableEntity
import com.github.lambda.domain.repository.CatalogTableRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Catalog Table JPA Repository Implementation
 *
 * Uses the Simplified Pattern: single interface extending both domain interface and JpaRepository.
 * Follows the same pattern as DatasetRepositoryJpaImpl for consistency.
 */
@Repository("catalogTableRepositoryJpa")
interface CatalogTableRepositoryJpaImpl :
    CatalogTableRepositoryJpa,
    JpaRepository<CatalogTableEntity, Long> {
    // === Basic CRUD Operations ===
    // save() is provided by JpaRepository

    override fun findByName(name: String): CatalogTableEntity?

    override fun existsByName(name: String): Boolean

    @Modifying
    @Query("DELETE FROM CatalogTableEntity c WHERE c.name = :name")
    override fun deleteByName(
        @Param("name") name: String,
    ): Long

    // === Project/Dataset Based Queries ===

    override fun findByProject(project: String): List<CatalogTableEntity>

    override fun findByProjectAndDatasetName(
        project: String,
        datasetName: String,
    ): List<CatalogTableEntity>

    // === Owner Based Queries ===

    override fun findByOwner(owner: String): List<CatalogTableEntity>

    override fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<CatalogTableEntity>

    // === Team Based Queries ===

    override fun findByTeam(team: String): List<CatalogTableEntity>

    // === Tag Based Queries ===

    @Query("SELECT c FROM CatalogTableEntity c WHERE :tag MEMBER OF c.tags")
    override fun findByTagsContaining(
        @Param("tag") tag: String,
    ): List<CatalogTableEntity>

    // === Engine Based Queries ===

    override fun findByEngine(engine: String): List<CatalogTableEntity>

    // === Pagination Queries ===

    override fun findAllByOrderByLastUpdatedDesc(pageable: Pageable): Page<CatalogTableEntity>

    // === Search Queries ===

    override fun findByNameContainingIgnoreCase(namePattern: String): List<CatalogTableEntity>

    override fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<CatalogTableEntity>

    // === Statistics ===

    override fun countByProject(project: String): Long

    override fun countByOwner(owner: String): Long

    // count() is provided by JpaRepository

    // === Custom Complex Queries ===

    @Query(
        """
        SELECT c FROM CatalogTableEntity c
        WHERE (:project IS NULL OR c.project = :project)
        AND (:dataset IS NULL OR c.datasetName = :dataset)
        AND (:owner IS NULL OR LOWER(c.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:team IS NULL OR c.team = :team)
        ORDER BY c.lastUpdated DESC NULLS LAST
        """,
    )
    fun findByComplexFilters(
        @Param("project") project: String?,
        @Param("dataset") dataset: String?,
        @Param("owner") owner: String?,
        @Param("team") team: String?,
        pageable: Pageable,
    ): Page<CatalogTableEntity>

    @Query(
        """
        SELECT c FROM CatalogTableEntity c
        WHERE (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:project IS NULL OR c.project = :project)
        ORDER BY c.lastUpdated DESC NULLS LAST
        """,
    )
    fun searchByKeyword(
        @Param("keyword") keyword: String,
        @Param("project") project: String?,
        pageable: Pageable,
    ): List<CatalogTableEntity>
}
