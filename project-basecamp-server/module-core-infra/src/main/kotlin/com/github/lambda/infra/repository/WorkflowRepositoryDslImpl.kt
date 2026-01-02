package com.github.lambda.infra.repository

import com.github.lambda.domain.model.workflow.QWorkflowEntity
import com.github.lambda.domain.model.workflow.WorkflowEntity
import com.github.lambda.domain.model.workflow.WorkflowSourceType
import com.github.lambda.domain.model.workflow.WorkflowStatus
import com.github.lambda.domain.repository.WorkflowRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Workflow Repository DSL 구현체
 *
 * QueryDSL을 사용하여 복잡한 쿼리 및 집계 작업을 구현합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("workflowRepositoryDsl")
class WorkflowRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : WorkflowRepositoryDsl {
    private val workflow = QWorkflowEntity.workflowEntity

    // Direct column access for embedded schedule.cron (bypasses protected Q-class field issue)
    private val scheduleCron = Expressions.stringPath(workflow, "schedule.cron")

    override fun findByFilters(
        sourceType: WorkflowSourceType?,
        status: WorkflowStatus?,
        owner: String?,
        team: String?,
        search: String?,
        pageable: Pageable,
    ): Page<WorkflowEntity> {
        val query = createFilterQuery(sourceType, status, owner, team, search)

        val content =
            query
                .orderBy(workflow.updatedAt.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        val total =
            createFilterCountQuery(sourceType, status, owner, team, search)
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun countByFilters(
        sourceType: WorkflowSourceType?,
        status: WorkflowStatus?,
        owner: String?,
        team: String?,
        search: String?,
    ): Long =
        createFilterCountQuery(sourceType, status, owner, team, search)
            .fetchOne() ?: 0L

    override fun getWorkflowStatistics(
        sourceType: WorkflowSourceType?,
        owner: String?,
    ): Map<String, Any> {
        val baseCondition = BooleanBuilder()
        sourceType?.let { baseCondition.and(workflow.sourceType.eq(it)) }
        owner?.let { baseCondition.and(workflow.owner.containsIgnoreCase(it)) }

        val totalCount =
            queryFactory
                .select(workflow.count())
                .from(workflow)
                .where(baseCondition)
                .fetchOne() ?: 0L

        val activeCount =
            queryFactory
                .select(workflow.count())
                .from(workflow)
                .where(baseCondition.and(workflow.status.eq(WorkflowStatus.ACTIVE)))
                .fetchOne() ?: 0L

        val pausedCount =
            queryFactory
                .select(workflow.count())
                .from(workflow)
                .where(baseCondition.and(workflow.status.eq(WorkflowStatus.PAUSED)))
                .fetchOne() ?: 0L

        val disabledCount =
            queryFactory
                .select(workflow.count())
                .from(workflow)
                .where(baseCondition.and(workflow.status.eq(WorkflowStatus.DISABLED)))
                .fetchOne() ?: 0L

        return mapOf(
            "totalCount" to totalCount,
            "activeCount" to activeCount,
            "pausedCount" to pausedCount,
            "disabledCount" to disabledCount,
            "sourceType" to (sourceType?.name ?: "ALL"),
            "owner" to (owner ?: "ALL"),
        )
    }

    override fun getWorkflowCountByStatus(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflow.status.stringValue().`as`("status"),
                    workflow.count().`as`("count"),
                ),
            ).from(workflow)
            .groupBy(workflow.status)
            .orderBy(workflow.status.asc())
            .fetch()
            .map { it as Map<String, Any> }

    override fun getWorkflowCountByOwner(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflow.owner.`as`("owner"),
                    workflow.count().`as`("count"),
                ),
            ).from(workflow)
            .groupBy(workflow.owner)
            .orderBy(workflow.count().desc())
            .limit(20) // Top 20
            .fetch()
            .map { it as Map<String, Any> }

    override fun getWorkflowCountBySourceType(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflow.sourceType.stringValue().`as`("sourceType"),
                    workflow.count().`as`("count"),
                ),
            ).from(workflow)
            .groupBy(workflow.sourceType)
            .orderBy(workflow.sourceType.asc())
            .fetch()
            .map { it as Map<String, Any> }

    override fun getWorkflowCountByTeam(): List<Map<String, Any>> =
        queryFactory
            .select(
                Projections.bean(
                    Map::class.java,
                    workflow.team.`as`("team"),
                    workflow.count().`as`("count"),
                ),
            ).from(workflow)
            .where(workflow.team.isNotNull)
            .groupBy(workflow.team)
            .orderBy(workflow.count().desc())
            .limit(20) // Top 20
            .fetch()
            .map { it as Map<String, Any> }

    override fun findRecentlyUpdated(
        limit: Int,
        daysSince: Int,
    ): List<WorkflowEntity> {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())

        return queryFactory
            .selectFrom(workflow)
            .where(workflow.updatedAt.goe(since))
            .orderBy(workflow.updatedAt.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findActiveScheduledWorkflows(): List<WorkflowEntity> =
        queryFactory
            .selectFrom(workflow)
            .where(
                workflow.status
                    .eq(WorkflowStatus.ACTIVE)
                    .and(scheduleCron.isNotNull),
            ).orderBy(workflow.updatedAt.desc())
            .fetch()

    override fun findWorkflowsBySchedule(cronPattern: String?): List<WorkflowEntity> {
        val condition =
            BooleanBuilder()
                .and(scheduleCron.isNotNull)

        cronPattern?.let {
            condition.and(scheduleCron.contains(it))
        }

        return queryFactory
            .selectFrom(workflow)
            .where(condition)
            .orderBy(workflow.updatedAt.desc())
            .fetch()
    }

    override fun findWorkflowsByAirflowDagId(
        dagIdPattern: String,
        includeDisabled: Boolean,
    ): List<WorkflowEntity> {
        val condition =
            BooleanBuilder()
                .and(workflow.airflowDagId.contains(dagIdPattern))

        if (!includeDisabled) {
            condition.and(workflow.status.eq(WorkflowStatus.ACTIVE))
        }

        return queryFactory
            .selectFrom(workflow)
            .where(condition)
            .orderBy(workflow.updatedAt.desc())
            .fetch()
    }

    override fun findActiveWorkflows(
        hasSchedule: Boolean?,
        sourceType: WorkflowSourceType?,
    ): List<WorkflowEntity> {
        val condition =
            BooleanBuilder()
                .and(workflow.status.eq(WorkflowStatus.ACTIVE))

        hasSchedule?.let {
            if (it) {
                condition.and(scheduleCron.isNotNull)
            } else {
                condition.and(scheduleCron.isNull)
            }
        }

        sourceType?.let {
            condition.and(workflow.sourceType.eq(it))
        }

        return queryFactory
            .selectFrom(workflow)
            .where(condition)
            .orderBy(workflow.updatedAt.desc())
            .fetch()
    }

    override fun getWorkflowCountByDatasetLevel(): Map<String, Any> {
        // 데이터셋 이름에서 catalog.schema.table 레벨로 분석
        val workflows =
            queryFactory
                .selectFrom(workflow)
                .fetch()

        val catalogs = workflows.map { it.getCatalog() }.groupingBy { it }.eachCount()
        val schemas = workflows.map { "${it.getCatalog()}.${it.getSchema()}" }.groupingBy { it }.eachCount()
        val tables = workflows.map { it.datasetName }.groupingBy { it }.eachCount()

        return mapOf(
            "catalogLevel" to
                catalogs
                    .map { (catalog, count) ->
                        mapOf("catalog" to catalog, "count" to count)
                    }.sortedByDescending { it["count"] as Int },
            "schemaLevel" to
                schemas
                    .map { (schema, count) ->
                        mapOf("schema" to schema, "count" to count)
                    }.sortedByDescending { it["count"] as Int },
            "tableLevel" to
                tables
                    .map { (table, count) ->
                        mapOf("table" to table, "count" to count)
                    }.sortedByDescending { it["count"] as Int },
        )
    }

    override fun findWorkflowsWithRuns(hasRecentRuns: Boolean?): List<WorkflowEntity> {
        val sevenDaysAgo = LocalDateTime.now().minusDays(7)

        val condition = BooleanBuilder()

        hasRecentRuns?.let {
            if (it) {
                // 최근 7일 내 실행 이력이 있는 워크플로우
                condition.and(
                    workflow.runs
                        .any()
                        .startedAt
                        .goe(sevenDaysAgo),
                )
            } else {
                // 최근 7일 내 실행 이력이 없는 워크플로우
                condition.and(
                    workflow.runs.isEmpty
                        .or(
                            workflow.runs
                                .any()
                                .startedAt
                                .lt(sevenDaysAgo),
                        ),
                )
            }
        }

        return queryFactory
            .selectFrom(workflow)
            .where(condition)
            .orderBy(workflow.updatedAt.desc())
            .fetch()
    }

    private fun createFilterQuery(
        sourceType: WorkflowSourceType?,
        status: WorkflowStatus?,
        owner: String?,
        team: String?,
        search: String?,
    ): JPAQuery<WorkflowEntity> {
        val condition = createFilterCondition(sourceType, status, owner, team, search)

        return queryFactory
            .selectFrom(workflow)
            .where(condition)
    }

    private fun createFilterCountQuery(
        sourceType: WorkflowSourceType?,
        status: WorkflowStatus?,
        owner: String?,
        team: String?,
        search: String?,
    ): JPAQuery<Long> {
        val condition = createFilterCondition(sourceType, status, owner, team, search)

        return queryFactory
            .select(workflow.count())
            .from(workflow)
            .where(condition)
    }

    private fun createFilterCondition(
        sourceType: WorkflowSourceType?,
        status: WorkflowStatus?,
        owner: String?,
        team: String?,
        search: String?,
    ): BooleanExpression? {
        val condition = BooleanBuilder()

        sourceType?.let { condition.and(workflow.sourceType.eq(it)) }
        status?.let { condition.and(workflow.status.eq(it)) }
        owner?.let { condition.and(workflow.owner.containsIgnoreCase(it)) }
        team?.let { condition.and(workflow.team.eq(it)) }
        search?.let {
            condition.and(
                workflow.datasetName
                    .containsIgnoreCase(it)
                    .or(workflow.description.containsIgnoreCase(it)),
            )
        }

        return if (condition.hasValue()) condition.value as? BooleanExpression else null
    }
}
