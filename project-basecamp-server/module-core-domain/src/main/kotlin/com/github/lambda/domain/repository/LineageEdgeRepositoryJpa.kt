package com.github.lambda.domain.repository

import com.github.lambda.domain.model.lineage.LineageEdgeEntity
import com.github.lambda.domain.model.lineage.LineageEdgeType
import java.util.*

/**
 * Lineage Edge Repository JPA Interface (Pure Domain Abstraction)
 *
 * Provides basic CRUD operations for LineageEdgeEntity.
 * Uses signatures compatible with JpaRepository to avoid conflicts.
 */
interface LineageEdgeRepositoryJpa {
    // Basic CRUD operations
    fun save(edge: LineageEdgeEntity): LineageEdgeEntity

    fun findById(id: Long): Optional<LineageEdgeEntity>

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<LineageEdgeEntity>

    // Domain-specific queries
    fun findBySource(source: String): List<LineageEdgeEntity>

    fun findByTarget(target: String): List<LineageEdgeEntity>

    fun findBySourceAndTarget(
        source: String,
        target: String,
    ): LineageEdgeEntity?

    fun findByEdgeType(edgeType: LineageEdgeType): List<LineageEdgeEntity>

    fun existsBySourceAndTarget(
        source: String,
        target: String,
    ): Boolean

    fun deleteBySourceAndTarget(
        source: String,
        target: String,
    )

    fun countBySource(source: String): Long

    fun countByTarget(target: String): Long
}
