package com.github.lambda.domain.repository

import com.github.lambda.domain.model.catalog.CatalogColumnEntity

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
