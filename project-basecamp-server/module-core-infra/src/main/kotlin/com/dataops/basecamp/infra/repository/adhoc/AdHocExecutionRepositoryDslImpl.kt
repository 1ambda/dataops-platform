package com.dataops.basecamp.infra.repository.adhoc

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.domain.entity.adhoc.AdHocExecutionEntity
import com.dataops.basecamp.domain.entity.adhoc.QAdHocExecutionEntity
import com.dataops.basecamp.domain.repository.adhoc.AdHocExecutionRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Ad-Hoc Execution QueryDSL Repository 구현
 *
 * 복잡한 동적 쿼리를 위한 QueryDSL 기반 구현입니다.
 */
@Repository("adHocExecutionRepositoryDsl")
class AdHocExecutionRepositoryDslImpl(
    private val jpaQueryFactory: JPAQueryFactory,
) : AdHocExecutionRepositoryDsl {
    private val execution = QAdHocExecutionEntity.adHocExecutionEntity

    override fun findByFilters(
        userId: String?,
        status: ExecutionStatus?,
        engine: String?,
        search: String?,
        fromDate: LocalDateTime?,
        toDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AdHocExecutionEntity> {
        val predicate = BooleanBuilder()

        userId?.let { predicate.and(execution.userId.eq(it)) }
        status?.let { predicate.and(execution.status.eq(it)) }
        engine?.let { predicate.and(execution.engine.equalsIgnoreCase(it)) }
        search?.let {
            predicate.and(
                execution.sqlQuery
                    .containsIgnoreCase(it)
                    .or(execution.queryId.containsIgnoreCase(it)),
            )
        }
        fromDate?.let { predicate.and(execution.createdAt.goe(it)) }
        toDate?.let { predicate.and(execution.createdAt.loe(it)) }

        val content =
            jpaQueryFactory
                .selectFrom(execution)
                .where(predicate)
                .orderBy(execution.createdAt.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        val total =
            jpaQueryFactory
                .select(execution.count())
                .from(execution)
                .where(predicate)
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun getExecutionStatistics(userId: String?): Map<String, Any> {
        val predicate = BooleanBuilder()
        userId?.let { predicate.and(execution.userId.eq(it)) }

        val total =
            jpaQueryFactory
                .select(execution.count())
                .from(execution)
                .where(predicate)
                .fetchOne() ?: 0L

        val completed =
            jpaQueryFactory
                .select(execution.count())
                .from(execution)
                .where(predicate.and(execution.status.eq(ExecutionStatus.COMPLETED)))
                .fetchOne() ?: 0L

        val failed =
            jpaQueryFactory
                .select(execution.count())
                .from(execution)
                .where(predicate.and(execution.status.eq(ExecutionStatus.FAILED)))
                .fetchOne() ?: 0L

        val avgExecutionTime =
            jpaQueryFactory
                .select(execution.executionTimeSeconds.avg())
                .from(execution)
                .where(predicate.and(execution.status.eq(ExecutionStatus.COMPLETED)))
                .fetchOne() ?: 0.0

        return mapOf(
            "totalExecutions" to total,
            "completedExecutions" to completed,
            "failedExecutions" to failed,
            "successRate" to if (total > 0) (completed.toDouble() / total * 100) else 0.0,
            "averageExecutionTimeSeconds" to avgExecutionTime,
        )
    }

    override fun countExecutionsByUserIdSince(
        userId: String,
        since: LocalDateTime,
    ): Long =
        jpaQueryFactory
            .select(execution.count())
            .from(execution)
            .where(
                execution.userId
                    .eq(userId)
                    .and(execution.createdAt.goe(since)),
            ).fetchOne() ?: 0L

    override fun findRecentExecutions(
        limit: Int,
        daysSince: Int,
    ): List<AdHocExecutionEntity> {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())

        return jpaQueryFactory
            .selectFrom(execution)
            .where(execution.createdAt.goe(since))
            .orderBy(execution.createdAt.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun getEngineStatistics(userId: String?): Map<String, Long> {
        val predicate = BooleanBuilder()
        userId?.let { predicate.and(execution.userId.eq(it)) }

        return jpaQueryFactory
            .select(execution.engine, execution.count())
            .from(execution)
            .where(predicate)
            .groupBy(execution.engine)
            .fetch()
            .associate { tuple ->
                (tuple.get(execution.engine) ?: "unknown") to (tuple.get(execution.count()) ?: 0L)
            }
    }

    override fun deleteExpiredResults(before: LocalDateTime): Long =
        jpaQueryFactory
            .delete(execution)
            .where(
                execution.expiresAt.isNotNull
                    .and(execution.expiresAt.lt(before))
                    .and(execution.status.eq(ExecutionStatus.COMPLETED)),
            ).execute()
}
