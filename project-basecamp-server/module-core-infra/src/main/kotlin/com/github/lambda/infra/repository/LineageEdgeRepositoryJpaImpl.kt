package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.lineage.LineageEdgeEntity
import com.github.lambda.domain.model.lineage.LineageEdgeType
import com.github.lambda.domain.repository.LineageEdgeRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Lineage Edge JPA Repository Implementation Interface
 *
 * Implements the domain LineageEdgeRepositoryJpa interface by extending JpaRepository directly.
 * This simplified pattern combines domain interface and Spring Data JPA into one interface.
 * Follows Pure Hexagonal Architecture pattern.
 */
@Repository("lineageEdgeRepositoryJpa")
interface LineageEdgeRepositoryJpaImpl :
    LineageEdgeRepositoryJpa,
    JpaRepository<LineageEdgeEntity, Long> {
    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findBySource(source: String): List<LineageEdgeEntity>

    override fun findByTarget(target: String): List<LineageEdgeEntity>

    override fun findBySourceAndTarget(
        source: String,
        target: String,
    ): LineageEdgeEntity?

    override fun findByEdgeType(edgeType: LineageEdgeType): List<LineageEdgeEntity>

    override fun existsBySourceAndTarget(
        source: String,
        target: String,
    ): Boolean

    override fun countBySource(source: String): Long

    override fun countByTarget(target: String): Long

    // Custom deletion query
    @Modifying
    @Query("DELETE FROM LineageEdgeEntity e WHERE e.source = :source AND e.target = :target")
    override fun deleteBySourceAndTarget(
        @Param("source") source: String,
        @Param("target") target: String,
    )
}
