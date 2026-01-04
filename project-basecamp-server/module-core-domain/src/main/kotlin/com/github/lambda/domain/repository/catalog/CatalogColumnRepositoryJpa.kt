package com.github.lambda.domain.repository.catalog

import com.github.lambda.domain.entity.catalog.CatalogColumnEntity

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
