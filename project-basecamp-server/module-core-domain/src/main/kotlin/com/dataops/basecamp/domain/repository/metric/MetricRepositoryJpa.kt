package com.dataops.basecamp.domain.repository.metric

import com.dataops.basecamp.domain.entity.metric.MetricEntity
import java.util.Optional

/**
 * Metric Repository JPA Interface (Pure Domain Abstraction)
 *
 * Defines basic CRUD operations and domain-specific queries.
 * Uses signatures compatible with JpaRepository to avoid conflicts.
 */
interface MetricRepositoryJpa {
    // Basic CRUD operations (JpaRepository provides: save, findById, deleteById, existsById, findAll)
    fun save(metric: MetricEntity): MetricEntity

    fun findById(id: Long): Optional<MetricEntity>

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<MetricEntity>

    // Domain-specific queries
    fun findByName(name: String): MetricEntity?

    fun existsByName(name: String): Boolean

    fun findByOwner(owner: String): List<MetricEntity>

    fun countByOwner(owner: String): Long
}
