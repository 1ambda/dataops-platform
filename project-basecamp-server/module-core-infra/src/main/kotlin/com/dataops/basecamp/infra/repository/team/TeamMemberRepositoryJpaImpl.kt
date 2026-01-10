package com.dataops.basecamp.infra.repository.team

import com.dataops.basecamp.domain.entity.team.TeamMemberEntity
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Team Member Repository JPA Implementation
 *
 * Implements TeamMemberRepositoryJpa interface using Spring Data JPA.
 */
@Repository("teamMemberRepositoryJpa")
interface TeamMemberRepositoryJpaImpl :
    TeamMemberRepositoryJpa,
    JpaRepository<TeamMemberEntity, Long> {
    // Spring Data JPA auto-implements methods with naming convention

    override fun findByIdAndDeletedAtIsNull(id: Long): TeamMemberEntity?

    override fun findByTeamIdAndUserIdAndDeletedAtIsNull(
        teamId: Long,
        userId: Long,
    ): TeamMemberEntity?

    override fun findByTeamIdAndDeletedAtIsNull(teamId: Long): List<TeamMemberEntity>

    override fun findByUserIdAndDeletedAtIsNull(userId: Long): List<TeamMemberEntity>

    override fun existsByTeamIdAndUserIdAndDeletedAtIsNull(
        teamId: Long,
        userId: Long,
    ): Boolean

    override fun countByTeamIdAndDeletedAtIsNull(teamId: Long): Long
}
