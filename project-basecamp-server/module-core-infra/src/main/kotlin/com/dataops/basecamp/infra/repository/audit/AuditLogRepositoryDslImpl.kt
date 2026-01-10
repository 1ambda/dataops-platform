package com.dataops.basecamp.infra.repository.audit

import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.domain.entity.audit.AuditLogEntity
import com.dataops.basecamp.domain.entity.audit.QAuditLogEntity
import com.dataops.basecamp.domain.repository.audit.AuditLogRepositoryDsl
import com.dataops.basecamp.domain.repository.audit.AuditStats
import com.dataops.basecamp.domain.repository.audit.UserAuditCount
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Audit Log Repository DSL Implementation
 *
 * Implements complex queries using QueryDSL.
 */
@Repository("auditLogRepositoryDsl")
class AuditLogRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : AuditLogRepositoryDsl {
    private val auditLog = QAuditLogEntity.auditLogEntity

    override fun findByUserId(
        userId: String,
        pageable: Pageable,
    ): Page<AuditLogEntity> {
        val content =
            queryFactory
                .selectFrom(auditLog)
                .where(auditLog.userId.eq(userId))
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .orderBy(auditLog.createdAt.desc())
                .fetch()

        val total =
            queryFactory
                .select(auditLog.count())
                .from(auditLog)
                .where(auditLog.userId.eq(userId))
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun findByAction(
        action: AuditAction,
        pageable: Pageable,
    ): Page<AuditLogEntity> {
        val content =
            queryFactory
                .selectFrom(auditLog)
                .where(auditLog.action.eq(action))
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .orderBy(auditLog.createdAt.desc())
                .fetch()

        val total =
            queryFactory
                .select(auditLog.count())
                .from(auditLog)
                .where(auditLog.action.eq(action))
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun findByResource(
        resource: AuditResource,
        pageable: Pageable,
    ): Page<AuditLogEntity> {
        val content =
            queryFactory
                .selectFrom(auditLog)
                .where(auditLog.resource.eq(resource))
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .orderBy(auditLog.createdAt.desc())
                .fetch()

        val total =
            queryFactory
                .select(auditLog.count())
                .from(auditLog)
                .where(auditLog.resource.eq(resource))
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun search(
        userId: String?,
        action: AuditAction?,
        resource: AuditResource?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogEntity> {
        val predicate = buildSearchPredicate(userId, action, resource, startDate, endDate)

        val content =
            queryFactory
                .selectFrom(auditLog)
                .where(predicate)
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .orderBy(auditLog.createdAt.desc())
                .fetch()

        val total =
            queryFactory
                .select(auditLog.count())
                .from(auditLog)
                .where(predicate)
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun getStats(
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): AuditStats {
        val datePredicate = buildDatePredicate(startDate, endDate)

        // Total count
        val totalCount =
            queryFactory
                .select(auditLog.count())
                .from(auditLog)
                .where(datePredicate)
                .fetchOne() ?: 0L

        // Action counts
        val actionCounts =
            queryFactory
                .select(auditLog.action, auditLog.count())
                .from(auditLog)
                .where(datePredicate)
                .groupBy(auditLog.action)
                .fetch()
                .associate { tuple ->
                    tuple.get(auditLog.action)!! to (tuple.get(auditLog.count()) ?: 0L)
                }

        // Resource counts
        val resourceCounts =
            queryFactory
                .select(auditLog.resource, auditLog.count())
                .from(auditLog)
                .where(datePredicate)
                .groupBy(auditLog.resource)
                .fetch()
                .associate { tuple ->
                    tuple.get(auditLog.resource)!! to (tuple.get(auditLog.count()) ?: 0L)
                }

        // Top users (top 10)
        val topUsers =
            queryFactory
                .select(auditLog.userId, auditLog.count())
                .from(auditLog)
                .where(datePredicate)
                .groupBy(auditLog.userId)
                .orderBy(auditLog.count().desc())
                .limit(10)
                .fetch()
                .map { tuple ->
                    UserAuditCount(
                        userId = tuple.get(auditLog.userId)!!,
                        count = tuple.get(auditLog.count()) ?: 0L,
                    )
                }

        // Average duration
        val avgDuration =
            queryFactory
                .select(auditLog.durationMs.avg())
                .from(auditLog)
                .where(datePredicate)
                .fetchOne() ?: 0.0

        return AuditStats(
            totalCount = totalCount,
            actionCounts = actionCounts,
            resourceCounts = resourceCounts,
            topUsers = topUsers,
            avgDurationMs = avgDuration,
        )
    }

    private fun buildSearchPredicate(
        userId: String?,
        action: AuditAction?,
        resource: AuditResource?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): BooleanBuilder {
        val builder = BooleanBuilder()

        userId?.let { builder.and(auditLog.userId.eq(it)) }
        action?.let { builder.and(auditLog.action.eq(it)) }
        resource?.let { builder.and(auditLog.resource.eq(it)) }
        startDate?.let { builder.and(auditLog.createdAt.goe(it)) }
        endDate?.let { builder.and(auditLog.createdAt.loe(it)) }

        return builder
    }

    private fun buildDatePredicate(
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): BooleanBuilder {
        val builder = BooleanBuilder()

        startDate?.let { builder.and(auditLog.createdAt.goe(it)) }
        endDate?.let { builder.and(auditLog.createdAt.loe(it)) }

        return builder
    }
}
