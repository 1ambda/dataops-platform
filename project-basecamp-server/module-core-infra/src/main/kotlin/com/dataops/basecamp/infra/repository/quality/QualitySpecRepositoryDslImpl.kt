package com.dataops.basecamp.infra.repository.quality

import com.dataops.basecamp.common.enums.ResourceType
import com.dataops.basecamp.domain.entity.quality.QQualitySpecEntity
import com.dataops.basecamp.domain.entity.quality.QualitySpecEntity
import com.dataops.basecamp.domain.projection.quality.QualitySpecCountByOwnerProjection
import com.dataops.basecamp.domain.projection.quality.QualitySpecCountByResourceTypeProjection
import com.dataops.basecamp.domain.projection.quality.QualitySpecCountByTagProjection
import com.dataops.basecamp.domain.projection.quality.QualitySpecCountByTeamProjection
import com.dataops.basecamp.domain.projection.quality.QualitySpecStatisticsProjection
import com.dataops.basecamp.domain.repository.quality.QualitySpecRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Quality Spec Repository DSL 구현체
 *
 * QueryDSL을 사용하여 복잡한 쿼리 및 집계 작업을 구현합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("qualitySpecRepositoryDsl")
class QualitySpecRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : QualitySpecRepositoryDsl {
    private val qualitySpec = QQualitySpecEntity.qualitySpecEntity

    override fun findByFilters(
        resourceType: ResourceType?,
        resourceName: String?,
        tag: String?,
        owner: String?,
        team: String?,
        enabled: Boolean?,
        search: String?,
        pageable: Pageable,
    ): Page<QualitySpecEntity> {
        val query = createFilterQuery(resourceType, resourceName, tag, owner, team, enabled, search)

        val content =
            query
                .orderBy(qualitySpec.updatedAt.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        val total =
            createFilterCountQuery(resourceType, resourceName, tag, owner, team, enabled, search)
                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun countByFilters(
        resourceType: ResourceType?,
        resourceName: String?,
        tag: String?,
        owner: String?,
        team: String?,
        enabled: Boolean?,
        search: String?,
    ): Long =
        createFilterCountQuery(resourceType, resourceName, tag, owner, team, enabled, search)
            .fetchOne() ?: 0L

    override fun getQualitySpecStatistics(
        resourceType: ResourceType?,
        owner: String?,
    ): QualitySpecStatisticsProjection {
        val baseCondition = BooleanBuilder()
        resourceType?.let { baseCondition.and(qualitySpec.resourceType.eq(it)) }
        owner?.let { baseCondition.and(qualitySpec.owner.containsIgnoreCase(it)) }

        val totalCount =
            queryFactory
                .select(qualitySpec.count())
                .from(qualitySpec)
                .where(baseCondition)
                .fetchOne() ?: 0L

        val enabledCount =
            queryFactory
                .select(qualitySpec.count())
                .from(qualitySpec)
                .where(baseCondition.and(qualitySpec.enabled.isTrue))
                .fetchOne() ?: 0L

        val disabledCount = totalCount - enabledCount

        // Get count by resource type with filters applied
        val specsByResourceType =
            queryFactory
                .select(qualitySpec.resourceType, qualitySpec.count())
                .from(qualitySpec)
                .where(baseCondition)
                .groupBy(qualitySpec.resourceType)
                .fetch()
                .associate { it.get(qualitySpec.resourceType)!! to it.get(qualitySpec.count())!! }

        return QualitySpecStatisticsProjection(
            totalSpecs = totalCount,
            enabledSpecs = enabledCount,
            disabledSpecs = disabledCount,
            specsByResourceType = specsByResourceType,
            specsByOwner = emptyMap(), // TODO: Implement proper aggregation
            specsByTeam = emptyMap(), // TODO: Implement proper aggregation
            specsWithSchedule = 0L, // TODO: Implement proper counting
            specsWithTests = 0L, // TODO: Implement proper counting
        )
    }

    override fun getQualitySpecCountByResourceType(): List<QualitySpecCountByResourceTypeProjection> =
        queryFactory
            .select(
                Projections.bean(
                    QualitySpecCountByResourceTypeProjection::class.java,
                    qualitySpec.resourceType.`as`("resourceType"),
                    qualitySpec.count().`as`("count"),
                ),
            ).from(qualitySpec)
            .groupBy(qualitySpec.resourceType)
            .orderBy(qualitySpec.resourceType.asc())
            .fetch()

    override fun getQualitySpecCountByOwner(): List<QualitySpecCountByOwnerProjection> =
        queryFactory
            .select(
                Projections.bean(
                    QualitySpecCountByOwnerProjection::class.java,
                    qualitySpec.owner.`as`("owner"),
                    qualitySpec.count().`as`("count"),
                ),
            ).from(qualitySpec)
            .groupBy(qualitySpec.owner)
            .orderBy(qualitySpec.count().desc())
            .limit(20) // Top 20
            .fetch()

    override fun getQualitySpecCountByTag(): List<QualitySpecCountByTagProjection> {
        // Tags는 ElementCollection이므로 별도 처리 필요
        return queryFactory
            .selectDistinct(qualitySpec.tags.any())
            .from(qualitySpec)
            .fetch()
            .groupingBy { it }
            .eachCount()
            .map { (tag, count) ->
                QualitySpecCountByTagProjection(
                    tag = tag,
                    count = count.toLong(),
                )
            }.sortedByDescending { it.count }
    }

    override fun getQualitySpecCountByTeam(): List<QualitySpecCountByTeamProjection> =
        queryFactory
            .select(
                Projections.bean(
                    QualitySpecCountByTeamProjection::class.java,
                    qualitySpec.team.`as`("team"),
                    qualitySpec.count().`as`("count"),
                ),
            ).from(qualitySpec)
            .where(qualitySpec.team.isNotNull)
            .groupBy(qualitySpec.team)
            .orderBy(qualitySpec.count().desc())
            .limit(20) // Top 20
            .fetch()

    override fun findRecentlyUpdatedQualitySpecs(
        limit: Int,
        daysSince: Int,
    ): List<QualitySpecEntity> {
        val since = LocalDateTime.now().minusDays(daysSince.toLong())

        return queryFactory
            .selectFrom(qualitySpec)
            .where(qualitySpec.updatedAt.goe(since))
            .orderBy(qualitySpec.updatedAt.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findScheduledQualitySpecs(cronPattern: String?): List<QualitySpecEntity> {
        val condition =
            BooleanBuilder()
                .and(qualitySpec.scheduleCron.isNotNull)

        cronPattern?.let {
            condition.and(qualitySpec.scheduleCron.contains(it))
        }

        return queryFactory
            .selectFrom(qualitySpec)
            .where(condition)
            .orderBy(qualitySpec.updatedAt.desc())
            .fetch()
    }

    override fun findQualitySpecsByResource(
        resourceName: String,
        resourceType: ResourceType,
        includeDisabled: Boolean,
    ): List<QualitySpecEntity> {
        val condition =
            BooleanBuilder()
                .and(qualitySpec.resourceName.eq(resourceName))
                .and(qualitySpec.resourceType.eq(resourceType))

        if (!includeDisabled) {
            condition.and(qualitySpec.enabled.isTrue)
        }

        return queryFactory
            .selectFrom(qualitySpec)
            .where(condition)
            .orderBy(qualitySpec.updatedAt.desc())
            .fetch()
    }

    override fun findActiveQualitySpecs(
        hasSchedule: Boolean?,
        hasTests: Boolean?,
    ): List<QualitySpecEntity> {
        val condition =
            BooleanBuilder()
                .and(qualitySpec.enabled.isTrue)

        hasSchedule?.let {
            if (it) {
                condition.and(qualitySpec.scheduleCron.isNotNull)
            } else {
                condition.and(qualitySpec.scheduleCron.isNull)
            }
        }

        // hasTests filter removed - tests relationship no longer exists
        // Use QualityTestRepositoryJpa.findBySpecId() instead to check for tests
        // TODO: Implement using subquery if hasTests filtering is needed

        return queryFactory
            .selectFrom(qualitySpec)
            .where(condition)
            .orderBy(qualitySpec.updatedAt.desc())
            .fetch()
    }

    private fun createFilterQuery(
        resourceType: ResourceType?,
        resourceName: String?,
        tag: String?,
        owner: String?,
        team: String?,
        enabled: Boolean?,
        search: String?,
    ): JPAQuery<QualitySpecEntity> {
        val condition = createFilterCondition(resourceType, resourceName, tag, owner, team, enabled, search)

        return queryFactory
            .selectFrom(qualitySpec)
            .where(condition)
    }

    private fun createFilterCountQuery(
        resourceType: ResourceType?,
        resourceName: String?,
        tag: String?,
        owner: String?,
        team: String?,
        enabled: Boolean?,
        search: String?,
    ): JPAQuery<Long> {
        val condition = createFilterCondition(resourceType, resourceName, tag, owner, team, enabled, search)

        return queryFactory
            .select(qualitySpec.count())
            .from(qualitySpec)
            .where(condition)
    }

    private fun createFilterCondition(
        resourceType: ResourceType?,
        resourceName: String?,
        tag: String?,
        owner: String?,
        team: String?,
        enabled: Boolean?,
        search: String?,
    ): BooleanBuilder {
        val condition = BooleanBuilder()

        resourceType?.let { condition.and(qualitySpec.resourceType.eq(it)) }
        resourceName?.let { condition.and(qualitySpec.resourceName.eq(it)) }
        tag?.let { condition.and(qualitySpec.tags.contains(it)) }
        owner?.let { condition.and(qualitySpec.owner.containsIgnoreCase(it)) }
        team?.let { condition.and(qualitySpec.team.eq(it)) }
        enabled?.let { condition.and(qualitySpec.enabled.eq(it)) }
        search?.let {
            condition.and(
                qualitySpec.name
                    .containsIgnoreCase(it)
                    .or(qualitySpec.description.containsIgnoreCase(it)),
            )
        }

        return condition
    }
}
