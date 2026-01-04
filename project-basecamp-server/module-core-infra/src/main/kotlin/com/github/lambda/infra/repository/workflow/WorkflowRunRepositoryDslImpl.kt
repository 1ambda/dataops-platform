package com.github.lambda.infra.repository.workflow

import com.github.lambda.common.enums.WorkflowRunStatus
import com.github.lambda.common.enums.WorkflowRunType
import com.github.lambda.domain.entity.workflow.QWorkflowRunEntity
import com.github.lambda.domain.entity.workflow.WorkflowRunEntity
import com.github.lambda.domain.projection.workflow.WorkflowDailyRunStatisticsProjection
import com.github.lambda.domain.projection.workflow.WorkflowDurationStatisticsProjection
import com.github.lambda.domain.projection.workflow.WorkflowRunCountByDatasetProjection
import com.github.lambda.domain.projection.workflow.WorkflowRunCountByStatusProjection
import com.github.lambda.domain.projection.workflow.WorkflowRunCountByTriggeredByProjection
import com.github.lambda.domain.projection.workflow.WorkflowRunCountByTypeProjection
import com.github.lambda.domain.projection.workflow.WorkflowRunStatisticsProjection
import com.github.lambda.domain.projection.workflow.WorkflowSuccessRateProjection
import com.github.lambda.domain.projection.workflow.WorkflowSyncStatisticsProjection
import com.github.lambda.domain.repository.workflow.WorkflowRunRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Workflow Run Repository DSL 구현체
 *
 * QueryDSL을 사용하여 복잡한 쿼리 및 집계 작업을 구현합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("workflowRunRepositoryDsl")
class WorkflowRunRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : WorkflowRunRepositoryDsl {
    private val workflowRun = QWorkflowRunEntity.workflowRunEntity

    override fun findRunsByFilters(
        datasetName: String?,
        status: WorkflowRunStatus?,
        runType: WorkflowRunType?,
        triggeredBy: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<WorkflowRunEntity> {
        val query = createFilterQuery(datasetName, status, runType, triggeredBy, startDate, endDate)

        val content =
            query
                .orderBy(workflowRun.startedAt.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        val total =
            createFilterCountQuery(datasetName, status, runType, triggeredBy, startDate, endDate)
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun countRunsByFilters(
        datasetName: String?,
        status: WorkflowRunStatus?,
        runType: WorkflowRunType?,
        triggeredBy: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): Long =
        createFilterCountQuery(datasetName, status, runType, triggeredBy, startDate, endDate)
            .fetchOne() ?: 0L

    override fun getRunStatistics(
        datasetName: String?,
        triggeredBy: String?,
    ): WorkflowRunStatisticsProjection {
        val baseCondition = BooleanBuilder()
        datasetName?.let { baseCondition.and(workflowRun.datasetName.eq(it)) }
        triggeredBy?.let { baseCondition.and(workflowRun.triggeredBy.containsIgnoreCase(it)) }

        val totalCount =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition)
                .fetchOne() ?: 0L

        val successCount =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition.and(workflowRun.status.eq(WorkflowRunStatus.SUCCESS)))
                .fetchOne() ?: 0L

        val failedCount =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition.and(workflowRun.status.eq(WorkflowRunStatus.FAILED)))
                .fetchOne() ?: 0L

        val runningCount =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition.and(workflowRun.status.eq(WorkflowRunStatus.RUNNING)))
                .fetchOne() ?: 0L

        val stoppedCount =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition.and(workflowRun.status.eq(WorkflowRunStatus.STOPPED)))
                .fetchOne() ?: 0L

        return WorkflowRunStatisticsProjection(
            totalRuns = totalCount,
            totalRunsByStatus =
                mapOf(
                    WorkflowRunStatus.SUCCESS to successCount,
                    WorkflowRunStatus.FAILED to failedCount,
                    WorkflowRunStatus.RUNNING to runningCount,
                    WorkflowRunStatus.STOPPED to stoppedCount,
                ),
            totalRunsByType = emptyMap(), // TODO: Implement proper aggregation
            totalRunsByTriggeredBy = emptyMap(), // TODO: Implement proper aggregation
            totalRunsByDatasetName = emptyMap(), // TODO: Implement proper aggregation
            averageDurationSeconds = null, // TODO: Implement duration calculation
        )
    }

    override fun getRunCountByStatus(): List<WorkflowRunCountByStatusProjection> =
        queryFactory
            .select(
                Projections.bean(
                    WorkflowRunCountByStatusProjection::class.java,
                    workflowRun.status.`as`("status"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.status)
            .orderBy(workflowRun.status.asc())
            .fetch()

    override fun getRunCountByRunType(): List<WorkflowRunCountByTypeProjection> =
        queryFactory
            .select(
                Projections.bean(
                    WorkflowRunCountByTypeProjection::class.java,
                    workflowRun.runType.`as`("runType"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.runType)
            .orderBy(workflowRun.runType.asc())
            .fetch()

    override fun getRunCountByTriggeredBy(): List<WorkflowRunCountByTriggeredByProjection> =
        queryFactory
            .select(
                Projections.bean(
                    WorkflowRunCountByTriggeredByProjection::class.java,
                    workflowRun.triggeredBy.`as`("triggeredBy"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.triggeredBy)
            .orderBy(workflowRun.count().desc())
            .limit(20) // Top 20
            .fetch()

    override fun getRunCountByDatasetName(): List<WorkflowRunCountByDatasetProjection> =
        queryFactory
            .select(
                Projections.bean(
                    WorkflowRunCountByDatasetProjection::class.java,
                    workflowRun.datasetName.`as`("datasetName"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.datasetName)
            .orderBy(workflowRun.count().desc())
            .limit(50) // Top 50
            .fetch()

    override fun getDurationStatistics(
        datasetName: String?,
        daysSince: Int,
    ): WorkflowDurationStatisticsProjection {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())
        val baseCondition =
            BooleanBuilder()
                .and(workflowRun.startedAt.goe(since))
                .and(workflowRun.endedAt.isNotNull)

        datasetName?.let { baseCondition.and(workflowRun.datasetName.eq(it)) }

        // Duration calculation using QueryDSL
        val durationExpression =
            Expressions.numberTemplate(
                Double::class.java,
                "EXTRACT(EPOCH FROM ({0} - {1}))",
                workflowRun.endedAt,
                workflowRun.startedAt,
            )

        val avgDuration =
            queryFactory
                .select(durationExpression.avg())
                .from(workflowRun)
                .where(baseCondition)
                .fetchOne() ?: 0.0

        val minDuration =
            queryFactory
                .select(durationExpression.min())
                .from(workflowRun)
                .where(baseCondition)
                .fetchOne() ?: 0.0

        val maxDuration =
            queryFactory
                .select(durationExpression.max())
                .from(workflowRun)
                .where(baseCondition)
                .fetchOne() ?: 0.0

        val totalRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition)
                .fetchOne() ?: 0L

        return WorkflowDurationStatisticsProjection(
            averageDurationSeconds = avgDuration,
            minDurationSeconds = minDuration,
            maxDurationSeconds = maxDuration,
            medianDurationSeconds = null, // TODO: Implement median calculation
            totalRuns = totalRuns,
        )
    }

    override fun findExecutionHistory(
        datasetName: String,
        limit: Int,
    ): List<WorkflowRunEntity> =
        queryFactory
            .selectFrom(workflowRun)
            .where(workflowRun.datasetName.eq(datasetName))
            .orderBy(workflowRun.startedAt.desc())
            .limit(limit.toLong())
            .fetch()

    override fun findLongRunningWorkflows(
        minDurationSeconds: Double,
        limit: Int,
    ): List<WorkflowRunEntity> {
        // Duration in seconds using QueryDSL
        val durationExpression =
            Expressions.numberTemplate(
                Double::class.java,
                "EXTRACT(EPOCH FROM ({0} - {1}))",
                workflowRun.endedAt,
                workflowRun.startedAt,
            )

        return queryFactory
            .selectFrom(workflowRun)
            .where(
                workflowRun.endedAt.isNotNull
                    .and(durationExpression.goe(minDurationSeconds)),
            ).orderBy(durationExpression.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findFailedRuns(
        datasetName: String?,
        daysSince: Int,
    ): List<WorkflowRunEntity> {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())
        val condition =
            BooleanBuilder()
                .and(workflowRun.status.eq(WorkflowRunStatus.FAILED))
                .and(workflowRun.startedAt.goe(since))

        datasetName?.let { condition.and(workflowRun.datasetName.eq(it)) }

        return queryFactory
            .selectFrom(workflowRun)
            .where(condition)
            .orderBy(workflowRun.startedAt.desc())
            .fetch()
    }

    override fun getSuccessRateStatistics(
        datasetName: String?,
        daysSince: Int,
    ): WorkflowSuccessRateProjection {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())
        val baseCondition =
            BooleanBuilder()
                .and(workflowRun.startedAt.goe(since))

        datasetName?.let { baseCondition.and(workflowRun.datasetName.eq(it)) }

        val totalRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition)
                .fetchOne() ?: 0L

        val successRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition.and(workflowRun.status.eq(WorkflowRunStatus.SUCCESS)))
                .fetchOne() ?: 0L

        val failedRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(baseCondition.and(workflowRun.status.eq(WorkflowRunStatus.FAILED)))
                .fetchOne() ?: 0L

        return WorkflowSuccessRateProjection(
            totalRuns = totalRuns,
            successfulRuns = successRuns,
            failedRuns = failedRuns,
            successRate = if (totalRuns > 0) (successRuns.toDouble() / totalRuns) else 0.0,
        )
    }

    override fun findCurrentlyRunningRuns(): List<WorkflowRunEntity> =
        queryFactory
            .selectFrom(workflowRun)
            .where(
                workflowRun.status.`in`(
                    WorkflowRunStatus.RUNNING,
                    WorkflowRunStatus.PENDING,
                    WorkflowRunStatus.STOPPING,
                ),
            ).orderBy(workflowRun.startedAt.desc())
            .fetch()

    override fun getDailyRunStatistics(
        datasetName: String?,
        daysSince: Int,
    ): List<WorkflowDailyRunStatisticsProjection> {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())
        val baseCondition =
            BooleanBuilder()
                .and(workflowRun.startedAt.goe(since))

        datasetName?.let { baseCondition.and(workflowRun.datasetName.eq(it)) }

        // Group by date using QueryDSL
        val dateExpression =
            Expressions.stringTemplate(
                "TO_CHAR({0}, 'YYYY-MM-DD')",
                workflowRun.startedAt,
            )

        // Conditional count using CaseBuilder
        val successCount =
            CaseBuilder()
                .`when`(workflowRun.status.eq(WorkflowRunStatus.SUCCESS))
                .then(1L)
                .otherwise(0L)
                .sum()

        val failedCount =
            CaseBuilder()
                .`when`(workflowRun.status.eq(WorkflowRunStatus.FAILED))
                .then(1L)
                .otherwise(0L)
                .sum()

        val results =
            queryFactory
                .select(
                    dateExpression,
                    workflowRun.count(),
                    successCount,
                    failedCount,
                ).from(workflowRun)
                .where(baseCondition)
                .groupBy(dateExpression)
                .orderBy(dateExpression.desc())
                .fetch()

        return results.map { tuple ->
            WorkflowDailyRunStatisticsProjection(
                date = tuple.get(dateExpression) ?: "",
                totalRuns = tuple.get(workflowRun.count()) ?: 0L,
                successfulRuns = tuple.get(successCount) ?: 0L,
                failedRuns = tuple.get(failedCount) ?: 0L,
                averageDurationSeconds = null, // TODO: Implement duration calculation
            )
        }
    }

    override fun findStoppedRuns(
        stoppedBy: String?,
        daysSince: Int,
    ): List<WorkflowRunEntity> {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())
        val condition =
            BooleanBuilder()
                .and(workflowRun.status.eq(WorkflowRunStatus.STOPPED))
                .and(workflowRun.stoppedAt.goe(since))

        stoppedBy?.let { condition.and(workflowRun.stoppedBy.eq(it)) }

        return queryFactory
            .selectFrom(workflowRun)
            .where(condition)
            .orderBy(workflowRun.stoppedAt.desc())
            .fetch()
    }

    override fun getAverageExecutionTime(
        datasetName: String,
        limit: Int,
    ): Double? {
        // Duration calculation using QueryDSL
        val durationExpression =
            Expressions.numberTemplate(
                Double::class.java,
                "EXTRACT(EPOCH FROM ({0} - {1}))",
                workflowRun.endedAt,
                workflowRun.startedAt,
            )

        return queryFactory
            .select(durationExpression.avg())
            .from(workflowRun)
            .where(
                workflowRun.datasetName
                    .eq(datasetName)
                    .and(workflowRun.status.eq(WorkflowRunStatus.SUCCESS))
                    .and(workflowRun.endedAt.isNotNull),
            ).orderBy(workflowRun.startedAt.desc())
            .limit(limit.toLong())
            .fetchOne()
    }

    private fun createFilterQuery(
        datasetName: String?,
        status: WorkflowRunStatus?,
        runType: WorkflowRunType?,
        triggeredBy: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): JPAQuery<WorkflowRunEntity> {
        val condition = createFilterCondition(datasetName, status, runType, triggeredBy, startDate, endDate)

        return queryFactory
            .selectFrom(workflowRun)
            .where(condition)
    }

    private fun createFilterCountQuery(
        datasetName: String?,
        status: WorkflowRunStatus?,
        runType: WorkflowRunType?,
        triggeredBy: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): JPAQuery<Long> {
        val condition = createFilterCondition(datasetName, status, runType, triggeredBy, startDate, endDate)

        return queryFactory
            .select(workflowRun.count())
            .from(workflowRun)
            .where(condition)
    }

    private fun createFilterCondition(
        datasetName: String?,
        status: WorkflowRunStatus?,
        runType: WorkflowRunType?,
        triggeredBy: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): BooleanExpression? {
        val condition = BooleanBuilder()

        datasetName?.let { condition.and(workflowRun.datasetName.eq(it)) }
        status?.let { condition.and(workflowRun.status.eq(it)) }
        runType?.let { condition.and(workflowRun.runType.eq(it)) }
        triggeredBy?.let { condition.and(workflowRun.triggeredBy.containsIgnoreCase(it)) }
        startDate?.let { condition.and(workflowRun.startedAt.goe(it)) }
        endDate?.let { condition.and(workflowRun.startedAt.loe(it)) }

        return if (condition.hasValue()) condition.value as? BooleanExpression else null
    }

    // === Airflow 동기화 관련 쿼리 (Phase 5) ===

    override fun findPendingRunsByCluster(
        clusterId: Long,
        since: LocalDateTime,
    ): List<WorkflowRunEntity> =
        queryFactory
            .selectFrom(workflowRun)
            .where(
                workflowRun.airflowClusterId.eq(clusterId),
                workflowRun.status.`in`(
                    WorkflowRunStatus.PENDING,
                    WorkflowRunStatus.RUNNING,
                    WorkflowRunStatus.STOPPING,
                ),
                workflowRun.createdAt.goe(since),
            ).orderBy(workflowRun.startedAt.desc())
            .fetch()

    override fun findStaleRuns(
        staleThreshold: LocalDateTime,
        statuses: List<WorkflowRunStatus>,
    ): List<WorkflowRunEntity> =
        queryFactory
            .selectFrom(workflowRun)
            .where(
                workflowRun.status.`in`(statuses),
                workflowRun.airflowDagRunId.isNotNull,
                workflowRun.lastSyncedAt
                    .lt(staleThreshold)
                    .or(workflowRun.lastSyncedAt.isNull),
            ).orderBy(workflowRun.lastSyncedAt.asc().nullsFirst())
            .fetch()

    override fun getSyncStatistics(clusterId: Long?): WorkflowSyncStatisticsProjection {
        val condition = BooleanBuilder()
        clusterId?.let { condition.and(workflowRun.airflowClusterId.eq(it)) }

        // Total runs count
        val totalRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(condition)
                .fetchOne() ?: 0L

        // Total synced count (with Airflow sync)
        val syncedRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(condition, workflowRun.airflowDagRunId.isNotNull)
                .fetchOne() ?: 0L

        // Pending sync (running but not recently synced)
        val oneHourAgo = LocalDateTime.now().minusHours(1)
        val pendingSyncRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(
                    condition,
                    workflowRun.status.`in`(WorkflowRunStatus.PENDING, WorkflowRunStatus.RUNNING),
                    workflowRun.airflowDagRunId.isNotNull,
                    workflowRun.lastSyncedAt
                        .lt(oneHourAgo)
                        .or(workflowRun.lastSyncedAt.isNull),
                ).fetchOne() ?: 0L

        // Stale runs (not synced for more than an hour)
        val staleRuns =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(
                    condition,
                    workflowRun.airflowDagRunId.isNotNull,
                    workflowRun.lastSyncedAt
                        .lt(oneHourAgo)
                        .or(workflowRun.lastSyncedAt.isNull),
                ).fetchOne() ?: 0L

        // Get most recent sync timestamp
        val lastSyncedAt =
            queryFactory
                .select(workflowRun.lastSyncedAt.max())
                .from(workflowRun)
                .where(condition, workflowRun.lastSyncedAt.isNotNull)
                .fetchOne()
                ?.toString()

        return WorkflowSyncStatisticsProjection(
            clusterId = clusterId,
            totalRuns = totalRuns,
            syncedRuns = syncedRuns,
            pendingSyncRuns = pendingSyncRuns,
            staleRuns = staleRuns,
            lastSyncedAt = lastSyncedAt,
        )
    }
}
