package com.dataops.basecamp.domain.repository.catalog

import com.dataops.basecamp.domain.entity.catalog.CatalogColumnEntity

/**
 * Catalog Column Repository JPA interface (Domain Port)
 *
 * Provides basic CRUD operations for CatalogColumnEntity.
 */
interface CatalogColumnRepositoryJpa {
    fun save(column: CatalogColumnEntity): CatalogColumnEntity

    fun findByCatalogTableId(catalogTableId: Long): List<CatalogColumnEntity>

    fun findByCatalogTableIdOrderByOrdinalPositionAsc(catalogTableId: Long): List<CatalogColumnEntity>
}
