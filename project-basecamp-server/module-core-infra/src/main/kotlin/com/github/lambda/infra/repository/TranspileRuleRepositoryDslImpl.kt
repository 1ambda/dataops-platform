package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.transpile.QTranspileRuleEntity
import com.github.lambda.domain.entity.transpile.TranspileRuleEntity
import com.github.lambda.domain.model.transpile.SqlDialect
import com.github.lambda.domain.repository.TranspileRuleRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * Transpile Rule Repository DSL Implementation (QueryDSL)
 *
 * Implements complex queries for transpile rules using QueryDSL.
 */
@Repository("transpileRuleRepositoryDsl")
class TranspileRuleRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : TranspileRuleRepositoryDsl {
    private val qRule = QTranspileRuleEntity.transpileRuleEntity

    override fun findByDialectsAndEnabled(
        fromDialect: SqlDialect?,
        toDialect: SqlDialect?,
        enabled: Boolean?,
    ): List<TranspileRuleEntity> {
        val builder = BooleanBuilder()

        // Build dynamic where conditions
        fromDialect?.let {
            builder.and(
                qRule.fromDialect.eq(it).or(qRule.fromDialect.eq(SqlDialect.ANY)),
            )
        }
        toDialect?.let {
            builder.and(
                qRule.toDialect.eq(it).or(qRule.toDialect.eq(SqlDialect.ANY)),
            )
        }
        enabled?.let { builder.and(qRule.enabled.eq(it)) }

        return queryFactory
            .selectFrom(qRule)
            .where(builder)
            .orderBy(qRule.priority.desc(), qRule.name.asc())
            .fetch()
    }

    override fun findApplicableRules(
        fromDialect: SqlDialect,
        toDialect: SqlDialect,
        orderByPriority: Boolean,
    ): List<TranspileRuleEntity> {
        val query =
            queryFactory
                .selectFrom(qRule)
                .where(
                    qRule.enabled.isTrue
                        .and(
                            qRule.fromDialect
                                .eq(fromDialect)
                                .or(qRule.fromDialect.eq(SqlDialect.ANY)),
                        ).and(
                            qRule.toDialect
                                .eq(toDialect)
                                .or(qRule.toDialect.eq(SqlDialect.ANY)),
                        ),
                )

        return if (orderByPriority) {
            query.orderBy(qRule.priority.desc(), qRule.name.asc()).fetch()
        } else {
            query.orderBy(qRule.name.asc()).fetch()
        }
    }

    override fun findByNameContaining(namePattern: String): List<TranspileRuleEntity> =
        queryFactory
            .selectFrom(qRule)
            .where(qRule.name.containsIgnoreCase(namePattern))
            .orderBy(qRule.name.asc())
            .fetch()

    override fun findByPriorityBetween(
        minPriority: Int,
        maxPriority: Int,
    ): List<TranspileRuleEntity> =
        queryFactory
            .selectFrom(qRule)
            .where(qRule.priority.between(minPriority, maxPriority))
            .orderBy(qRule.priority.desc(), qRule.name.asc())
            .fetch()

    override fun getRuleCountByDialectPair(): Map<String, Long> {
        val results =
            queryFactory
                .select(
                    qRule.fromDialect,
                    qRule.toDialect,
                    qRule.count(),
                ).from(qRule)
                .where(qRule.enabled.isTrue)
                .groupBy(qRule.fromDialect, qRule.toDialect)
                .fetch()

        return results.associate { tuple ->
            val from = tuple.get(qRule.fromDialect)?.name ?: "UNKNOWN"
            val to = tuple.get(qRule.toDialect)?.name ?: "UNKNOWN"
            val count = tuple.get(qRule.count()) ?: 0L
            "$from->$to" to count
        }
    }
}
