package com.dataops.basecamp.domain.repository.team

import com.dataops.basecamp.domain.entity.team.TeamMemberEntity

/**
 * Team Member Repository JPA Interface
 *
 * Defines basic CRUD operations for TeamMemberEntity.
 */
interface TeamMemberRepositoryJpa {
    fun save(member: TeamMemberEntity): TeamMemberEntity

    fun findByIdAndDeletedAtIsNull(id: Long): TeamMemberEntity?

    fun findByTeamIdAndUserIdAndDeletedAtIsNull(
        teamId: Long,
        userId: Long,
    ): TeamMemberEntity?

    fun findByTeamIdAndDeletedAtIsNull(teamId: Long): List<TeamMemberEntity>

    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<TeamMemberEntity>

    fun existsByTeamIdAndUserIdAndDeletedAtIsNull(
        teamId: Long,
        userId: Long,
    ): Boolean

    fun countByTeamIdAndDeletedAtIsNull(teamId: Long): Long
}
