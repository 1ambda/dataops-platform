package com.dataops.basecamp.infra.repository.team

import com.dataops.basecamp.common.enums.TeamResourceType
import com.dataops.basecamp.domain.entity.team.QTeamEntity
import com.dataops.basecamp.domain.entity.team.QTeamMemberEntity
import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.projection.team.TeamResourceCheckResult
import com.dataops.basecamp.domain.projection.team.TeamStatisticsProjection
import com.dataops.basecamp.domain.repository.team.TeamRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

/**
 * Team Repository DSL Implementation
 *
 * Implements complex queries using QueryDSL.
 */
@Repository("teamRepositoryDsl")
class TeamRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : TeamRepositoryDsl {
    private val team = QTeamEntity.teamEntity
    private val teamMember = QTeamMemberEntity.teamMemberEntity

    override fun findByConditions(
        name: String?,
        page: Int,
        size: Int,
    ): Page<TeamEntity> {
        val query =
            queryFactory
                .selectFrom(team)
                .where(
                    team.deletedAt.isNull,
                    name?.let { team.name.containsIgnoreCase(it) },
                ).orderBy(team.createdAt.desc())

        val total = query.fetch().size.toLong()

        val results =
            queryFactory
                .selectFrom(team)
                .where(
                    team.deletedAt.isNull,
                    name?.let { team.name.containsIgnoreCase(it) },
                ).orderBy(team.createdAt.desc())
                .offset((page * size).toLong())
                .limit(size.toLong())
                .fetch()

        return PageImpl(results, PageRequest.of(page, size), total)
    }

    override fun hasResources(teamId: Long): TeamResourceCheckResult {
        // Count team members
        val memberCount =
            queryFactory
                .select(teamMember.count())
                .from(teamMember)
                .where(
                    teamMember.teamId.eq(teamId),
                    teamMember.deletedAt.isNull,
                ).fetchOne() ?: 0L

        // For Phase 1, we only check members.
        // In future phases, add checks for:
        // - SqlFolder, SqlWorksheet, Metric, Dataset, Workflow, Quality, GitHubRepo
        // These entities need teamId FK to be added first.

        val hasResources = memberCount > 0

        return TeamResourceCheckResult(
            hasResources = hasResources,
            memberCount = memberCount.toInt(),
        )
    }

    override fun getTeamStatistics(teamId: Long): TeamStatisticsProjection? {
        // Check if team exists
        val teamExists =
            queryFactory
                .selectOne()
                .from(team)
                .where(
                    team.id.eq(teamId),
                    team.deletedAt.isNull,
                ).fetchFirst() != null

        if (!teamExists) {
            return null
        }

        // Count members
        val memberCount =
            queryFactory
                .select(teamMember.count())
                .from(teamMember)
                .where(
                    teamMember.teamId.eq(teamId),
                    teamMember.deletedAt.isNull,
                ).fetchOne()
                ?.toInt() ?: 0

        // For Phase 1, resource counts are empty.
        // In future phases, add counts for each resource type.
        val resourceCounts = emptyMap<TeamResourceType, Int>()

        return TeamStatisticsProjection(
            teamId = teamId,
            memberCount = memberCount,
            resourceCounts = resourceCounts,
        )
    }
}
