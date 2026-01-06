package com.dataops.basecamp.infra.repository.execution

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.common.enums.ExecutionType
import com.dataops.basecamp.domain.entity.execution.ExecutionHistoryEntity
import com.dataops.basecamp.domain.entity.execution.QExecutionHistoryEntity
import com.dataops.basecamp.domain.repository.execution.ExecutionHistoryRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Execution History Repository DSL Implementation
 *
 * QueryDSL을 사용하여 복잡한 쿼리를 구현합니다.
 */
@Repository("executionHistoryRepositoryDsl")
class ExecutionHistoryRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : ExecutionHistoryRepositoryDsl {
    private val executionHistory = QExecutionHistoryEntity.executionHistoryEntity

    override fun findByUserId(
        userId: Long,
        limit: Int,
    ): List<ExecutionHistoryEntity> =
        queryFactory
            .selectFrom(executionHistory)
            .where(executionHistory.userId.eq(userId))
            .orderBy(executionHistory.startedAt.desc())
            .limit(limit.toLong())
            .fetch()

    override fun findByTypeAndStatus(
        type: ExecutionType,
        status: ExecutionStatus,
    ): List<ExecutionHistoryEntity> =
        queryFactory
            .selectFrom(executionHistory)
            .where(
                executionHistory.executionType
                    .eq(type)
                    .and(executionHistory.status.eq(status)),
            ).orderBy(executionHistory.startedAt.desc())
            .fetch()

    override fun findByResourceName(
        resourceName: String,
        limit: Int,
    ): List<ExecutionHistoryEntity> =
        queryFactory
            .selectFrom(executionHistory)
            .where(executionHistory.resourceName.eq(resourceName))
            .orderBy(executionHistory.startedAt.desc())
            .limit(limit.toLong())
            .fetch()

    override fun findByTimeRange(
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<ExecutionHistoryEntity> {
        val condition =
            BooleanBuilder()
                .and(executionHistory.startedAt.goe(start))
                .and(executionHistory.startedAt.loe(end))

        return queryFactory
            .selectFrom(executionHistory)
            .where(condition)
            .orderBy(executionHistory.startedAt.desc())
            .fetch()
    }
}
