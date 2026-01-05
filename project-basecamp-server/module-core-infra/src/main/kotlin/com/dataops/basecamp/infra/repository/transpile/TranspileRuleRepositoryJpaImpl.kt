package com.dataops.basecamp.infra.repository.transpile

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.transpile.TranspileRuleEntity
import com.dataops.basecamp.domain.repository.transpile.TranspileRuleRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Transpile Rule JPA Repository Implementation
 *
 * Implements the domain TranspileRuleRepositoryJpa interface by extending JpaRepository directly.
 * Follows Pure Hexagonal Architecture pattern.
 */
@Repository("transpileRuleRepositoryJpa")
interface TranspileRuleRepositoryJpaImpl :
    TranspileRuleRepositoryJpa,
    JpaRepository<TranspileRuleEntity, Long> {
    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findByName(name: String): TranspileRuleEntity?

    override fun existsByName(name: String): Boolean

    override fun findByEnabledTrue(): List<TranspileRuleEntity>

    override fun findByFromDialectAndToDialect(
        fromDialect: SqlDialect,
        toDialect: SqlDialect,
    ): List<TranspileRuleEntity>

    override fun findByFromDialectAndToDialectAndEnabledTrue(
        fromDialect: SqlDialect,
        toDialect: SqlDialect,
    ): List<TranspileRuleEntity>

    override fun countByEnabledTrue(): Long
}
