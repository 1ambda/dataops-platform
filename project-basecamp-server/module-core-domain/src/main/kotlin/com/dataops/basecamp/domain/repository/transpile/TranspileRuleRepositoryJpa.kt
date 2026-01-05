package com.dataops.basecamp.domain.repository.transpile

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.transpile.TranspileRuleEntity
import java.util.Optional

/**
 * Transpile Rule Repository JPA Interface (Pure Domain Abstraction)
 *
 * Defines basic CRUD operations and domain-specific queries for transpile rules.
 */
interface TranspileRuleRepositoryJpa {
    // Basic CRUD operations
    fun save(rule: TranspileRuleEntity): TranspileRuleEntity

    fun findById(id: Long): Optional<TranspileRuleEntity>

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<TranspileRuleEntity>

    // Domain-specific queries
    fun findByName(name: String): TranspileRuleEntity?

    fun existsByName(name: String): Boolean

    fun findByEnabledTrue(): List<TranspileRuleEntity>

    fun findByFromDialectAndToDialect(
        fromDialect: SqlDialect,
        toDialect: SqlDialect,
    ): List<TranspileRuleEntity>

    fun findByFromDialectAndToDialectAndEnabledTrue(
        fromDialect: SqlDialect,
        toDialect: SqlDialect,
    ): List<TranspileRuleEntity>

    fun countByEnabledTrue(): Long
}
