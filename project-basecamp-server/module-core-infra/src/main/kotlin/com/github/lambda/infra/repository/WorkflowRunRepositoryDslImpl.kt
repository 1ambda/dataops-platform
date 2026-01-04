package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.workflow.QWorkflowRunEntity
import com.github.lambda.domain.entity.workflow.WorkflowRunEntity
import com.github.lambda.domain.model.workflow.WorkflowRunStatus
import com.github.lambda.domain.model.workflow.WorkflowRunType
import com.github.lambda.domain.repository.WorkflowRunRepositoryDsl
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
    ): Map<String, Any> {
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

        return mapOf(
            "totalCount" to totalCount,
            "successCount" to successCount,
            "failedCount" to failedCount,
            "runningCount" to runningCount,
            "stoppedCount" to stoppedCount,
            "successRate" to if (totalCount > 0) (successCount.toDouble() / totalCount * 100) else 0.0,
            "datasetName" to (datasetName ?: "ALL"),
            "triggeredBy" to (triggeredBy ?: "ALL"),
        )
    }

    override fun getRunCountByStatus(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflowRun.status.stringValue().`as`("status"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.status)
            .orderBy(workflowRun.status.asc())
            .fetch()
            .map { it as Map<String, Any> }

    override fun getRunCountByRunType(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflowRun.runType.stringValue().`as`("runType"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.runType)
            .orderBy(workflowRun.runType.asc())
            .fetch()
            .map { it as Map<String, Any> }

    override fun getRunCountByTriggeredBy(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflowRun.triggeredBy.`as`("triggeredBy"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.triggeredBy)
            .orderBy(workflowRun.count().desc())
            .limit(20) // Top 20
            .fetch()
            .map { it as Map<String, Any> }

    override fun getRunCountByDatasetName(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflowRun.datasetName.`as`("datasetName"),
                    workflowRun.count().`as`("count"),
                ),
            ).from(workflowRun)
            .groupBy(workflowRun.datasetName)
            .orderBy(workflowRun.count().desc())
            .limit(50) // Top 50
            .fetch()
            .map { it as Map<String, Any> }

    override fun getDurationStatistics(
        datasetName: String?,
        daysSince: Int,
    ): Map<String, Any> {
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

        return mapOf(
            "averageDuration" to avgDuration,
            "minDuration" to minDuration,
            "maxDuration" to maxDuration,
            "totalRuns" to totalRuns,
            "datasetName" to (datasetName ?: "ALL"),
            "daysSince" to daysSince,
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
    ): Map<String, Any> {
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

        return mapOf(
            "totalRuns" to totalRuns,
            "successRuns" to successRuns,
            "failedRuns" to failedRuns,
            "successRate" to if (totalRuns > 0) (successRuns.toDouble() / totalRuns * 100) else 0.0,
            "failureRate" to if (totalRuns > 0) (failedRuns.toDouble() / totalRuns * 100) else 0.0,
            "datasetName" to (datasetName ?: "ALL"),
            "daysSince" to daysSince,
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
    ): List<Map<String, Any>> {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())
        val baseCondition =
            BooleanBuilder()
                .and(workflowRun.startedAt.goe(since))

        datasetName?.let { baseCondition.and(workflowRun.datasetName.eq(it)) }

        // Group by date using QueryDSL
        val dateExpression =
            Expressions.dateTemplate(
                java.sql.Date::class.java,
                "DATE({0})",
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

        return queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    dateExpression.`as`("date"),
                    workflowRun.count().`as`("totalRuns"),
                    successCount.`as`("successRuns"),
                    failedCount.`as`("failedRuns"),
                ),
            ).from(workflowRun)
            .where(baseCondition)
            .groupBy(dateExpression)
            .orderBy(dateExpression.desc())
            .fetch()
            .map { it as Map<String, Any> }
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

    override fun getSyncStatistics(clusterId: Long?): Map<String, Any> {
        val condition = BooleanBuilder()
        clusterId?.let { condition.and(workflowRun.airflowClusterId.eq(it)) }

        // Total count with Airflow sync
        val totalSynced =
            queryFactory
                .select(workflowRun.count())
                .from(workflowRun)
                .where(condition, workflowRun.airflowDagRunId.isNotNull)
                .fetchOne() ?: 0L

        // Pending sync (running but not recently synced)
        val oneHourAgo = LocalDateTime.now().minusHours(1)
        val pendingSync =
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

        // Count by status
        val statusCounts =
            queryFactory
                .select(workflowRun.status, workflowRun.count())
                .from(workflowRun)
                .where(condition, workflowRun.airflowDagRunId.isNotNull)
                .groupBy(workflowRun.status)
                .fetch()
                .associate { tuple ->
                    tuple.get(workflowRun.status)!!.name to (tuple.get(workflowRun.count()) ?: 0L)
                }

        return mapOf(
            "totalSynced" to totalSynced,
            "pendingSync" to pendingSync,
            "statusCounts" to statusCounts,
            "clusterId" to (clusterId ?: "all"),
        )
    }
}
