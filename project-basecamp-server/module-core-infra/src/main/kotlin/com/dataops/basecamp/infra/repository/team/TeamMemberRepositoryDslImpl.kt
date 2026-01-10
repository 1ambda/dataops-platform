package com.dataops.basecamp.infra.repository.team

import com.dataops.basecamp.common.enums.TeamRole
import com.dataops.basecamp.domain.entity.team.QTeamMemberEntity
import com.dataops.basecamp.domain.entity.user.QUserEntity
import com.dataops.basecamp.domain.projection.team.TeamMemberWithUserProjection
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryDsl
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * Team Member Repository DSL Implementation
 *
 * Implements complex queries using QueryDSL.
 */
@Repository("teamMemberRepositoryDsl")
class TeamMemberRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : TeamMemberRepositoryDsl {
    private val teamMember = QTeamMemberEntity.teamMemberEntity
    private val user = QUserEntity.userEntity

    override fun findMembersWithUserByTeamId(teamId: Long): List<TeamMemberWithUserProjection> =
        queryFactory
            .select(
                Projections.constructor(
                    TeamMemberWithUserProjection::class.java,
                    teamMember.id,
                    teamMember.userId,
                    user.username,
                    user.email,
                    teamMember.role,
                    teamMember.createdAt,
                ),
            ).from(teamMember)
            .join(user)
            .on(user.id.eq(teamMember.userId))
            .where(
                teamMember.teamId.eq(teamId),
                teamMember.deletedAt.isNull,
                user.deletedAt.isNull,
            ).orderBy(teamMember.createdAt.asc())
            .fetch()

    override fun findTeamIdsByUserIdAndRoles(
        userId: Long,
        roles: List<TeamRole>?,
    ): List<Long> {
        val query =
            queryFactory
                .select(teamMember.teamId)
                .from(teamMember)
                .where(
                    teamMember.userId.eq(userId),
                    teamMember.deletedAt.isNull,
                )

        if (!roles.isNullOrEmpty()) {
            query.where(teamMember.role.`in`(roles))
        }

        return query.fetch()
    }

    override fun hasRoleInTeam(
        teamId: Long,
        userId: Long,
        role: TeamRole,
    ): Boolean =
        queryFactory
            .selectOne()
            .from(teamMember)
            .where(
                teamMember.teamId.eq(teamId),
                teamMember.userId.eq(userId),
                teamMember.role.eq(role),
                teamMember.deletedAt.isNull,
            ).fetchFirst() != null

    override fun hasAnyRoleInTeam(
        teamId: Long,
        userId: Long,
        roles: List<TeamRole>,
    ): Boolean {
        if (roles.isEmpty()) return false

        return queryFactory
            .selectOne()
            .from(teamMember)
            .where(
                teamMember.teamId.eq(teamId),
                teamMember.userId.eq(userId),
                teamMember.role.`in`(roles),
                teamMember.deletedAt.isNull,
            ).fetchFirst() != null
    }
}
