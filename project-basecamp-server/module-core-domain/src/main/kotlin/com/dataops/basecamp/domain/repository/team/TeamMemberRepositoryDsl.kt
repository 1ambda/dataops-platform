package com.dataops.basecamp.domain.repository.team

import com.dataops.basecamp.common.enums.TeamRole
import com.dataops.basecamp.domain.projection.team.TeamMemberWithUserProjection

/**
 * Team Member Repository DSL Interface
 *
 * Defines complex query operations for TeamMemberEntity using QueryDSL.
 */
interface TeamMemberRepositoryDsl {
    /**
     * Find team members with user details for a specific team.
     */
    fun findMembersWithUserByTeamId(teamId: Long): List<TeamMemberWithUserProjection>

    /**
     * Find teams where user is a member with specified roles.
     */
    fun findTeamIdsByUserIdAndRoles(
        userId: Long,
        roles: List<TeamRole>? = null,
    ): List<Long>

    /**
     * Check if user has specific role in team.
     */
    fun hasRoleInTeam(
        teamId: Long,
        userId: Long,
        role: TeamRole,
    ): Boolean

    /**
     * Check if user has any of the specified roles in team.
     */
    fun hasAnyRoleInTeam(
        teamId: Long,
        userId: Long,
        roles: List<TeamRole>,
    ): Boolean
}
