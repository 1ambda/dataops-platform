package com.github.lambda.infra.repository.catalog

import com.github.lambda.domain.entity.catalog.CatalogColumnEntity
import com.github.lambda.domain.repository.catalog.CatalogColumnRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Catalog Column JPA Repository Implementation Interface
 *
 * Implements the domain CatalogColumnRepositoryJpa interface by extending JpaRepository directly.
 * Follows Pure Hexagonal Architecture pattern.
 */
@Repository("catalogColumnRepositoryJpa")
interface CatalogColumnRepositoryJpaImpl :
    CatalogColumnRepositoryJpa,
    JpaRepository<CatalogColumnEntity, Long> {
    override fun findByCatalogTableId(catalogTableId: Long): List<CatalogColumnEntity>

    override fun findByCatalogTableIdOrderByOrdinalPositionAsc(catalogTableId: Long): List<CatalogColumnEntity>
}
