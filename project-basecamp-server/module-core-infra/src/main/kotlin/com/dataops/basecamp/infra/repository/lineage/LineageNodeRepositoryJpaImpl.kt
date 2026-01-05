package com.dataops.basecamp.infra.repository.lineage

import com.dataops.basecamp.common.enums.LineageNodeType
import com.dataops.basecamp.domain.entity.lineage.LineageNodeEntity
import com.dataops.basecamp.domain.repository.lineage.LineageNodeRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Lineage Node JPA Repository Implementation Interface
 *
 * Implements the domain LineageNodeRepositoryJpa interface by extending JpaRepository directly.
 * This simplified pattern combines domain interface and Spring Data JPA into one interface.
 * Follows Pure Hexagonal Architecture pattern.
 */
@Repository("lineageNodeRepositoryJpa")
interface LineageNodeRepositoryJpaImpl :
    LineageNodeRepositoryJpa,
    JpaRepository<LineageNodeEntity, String> {
    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findByName(name: String): LineageNodeEntity?

    override fun existsByName(name: String): Boolean

    override fun findByType(type: LineageNodeType): List<LineageNodeEntity>

    override fun findByOwner(owner: String): List<LineageNodeEntity>

    override fun findByTeam(team: String): List<LineageNodeEntity>

    override fun countByType(type: LineageNodeType): Long

    override fun countByOwner(owner: String): Long
}
