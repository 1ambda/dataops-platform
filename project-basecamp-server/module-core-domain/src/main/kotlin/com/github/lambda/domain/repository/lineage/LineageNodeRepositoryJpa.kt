package com.github.lambda.domain.repository.lineage

import com.github.lambda.common.enums.LineageNodeType
import com.github.lambda.domain.entity.lineage.LineageNodeEntity
import java.util.*

/**
 * Lineage Node Repository JPA Interface (Pure Domain Abstraction)
 *
 * Provides basic CRUD operations for LineageNodeEntity.
 * Uses signatures compatible with JpaRepository to avoid conflicts.
 */
interface LineageNodeRepositoryJpa {
    // Basic CRUD operations
    fun save(node: LineageNodeEntity): LineageNodeEntity

    fun findById(name: String): Optional<LineageNodeEntity>

    fun deleteById(name: String)

    fun existsById(name: String): Boolean

    fun findAll(): List<LineageNodeEntity>

    // Domain-specific queries
    fun findByName(name: String): LineageNodeEntity?

    fun existsByName(name: String): Boolean

    fun findByType(type: LineageNodeType): List<LineageNodeEntity>

    fun findByOwner(owner: String): List<LineageNodeEntity>

    fun findByTeam(team: String): List<LineageNodeEntity>

    fun countByType(type: LineageNodeType): Long

    fun countByOwner(owner: String): Long
}
