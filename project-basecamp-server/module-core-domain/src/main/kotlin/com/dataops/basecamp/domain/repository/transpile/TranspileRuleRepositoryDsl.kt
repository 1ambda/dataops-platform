package com.dataops.basecamp.domain.repository.transpile

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.transpile.TranspileRuleEntity

/**
 * Transpile Rule Repository DSL Interface (QueryDSL Complex Queries)
 *
 * Defines complex queries and filtering operations for transpile rules.
 */
interface TranspileRuleRepositoryDsl {
    /**
     * Find rules by dialect pair with flexible matching
     *
     * @param fromDialect Source dialect (null means any)
     * @param toDialect Target dialect (null means any)
     * @param enabled Filter by enabled status (null means all)
     * @return Rules ordered by priority (highest first)
     */
    fun findByDialectsAndEnabled(
        fromDialect: SqlDialect?,
        toDialect: SqlDialect?,
        enabled: Boolean? = true,
    ): List<TranspileRuleEntity>

    /**
     * Find applicable rules for SQL transpilation
     *
     * @param fromDialect Source dialect
     * @param toDialect Target dialect
     * @param orderByPriority Whether to order by priority (default: true)
     * @return Enabled rules applicable to the dialect pair, ordered by priority
     */
    fun findApplicableRules(
        fromDialect: SqlDialect,
        toDialect: SqlDialect,
        orderByPriority: Boolean = true,
    ): List<TranspileRuleEntity>

    /**
     * Find rules by name pattern (for admin searches)
     */
    fun findByNameContaining(namePattern: String): List<TranspileRuleEntity>

    /**
     * Find rules by priority range
     */
    fun findByPriorityBetween(
        minPriority: Int,
        maxPriority: Int,
    ): List<TranspileRuleEntity>

    /**
     * Get rule statistics by dialect
     */
    fun getRuleCountByDialectPair(): Map<String, Long>
}
